package com.rednavis.metaldesk.admin.quote;

import com.rednavis.metaldesk.admin.order.OrderAdminService;
import com.rednavis.metaldesk.admin.order.dto.OrderDetailView;
import com.rednavis.metaldesk.admin.order.dto.OrderSummaryView;
import com.rednavis.metaldesk.admin.order.dto.PageView;
import com.rednavis.metaldesk.admin.persistence.CustomerRepository;
import com.rednavis.metaldesk.admin.persistence.ManagerQuoteRepository;
import com.rednavis.metaldesk.admin.persistence.OrderStore;
import com.rednavis.metaldesk.admin.quote.dto.QuoteOutcome;
import com.rednavis.metaldesk.admin.security.StaffPrincipal;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.persistence.mapper.ManagerQuoteMapper;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.ManagerQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.OrderTransitions;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles the orders waiting for a manager quote (BRD FR-5.3).
 *
 * <p>Both outcomes are triggers on the order state machine, never a status assignment: {@code
 * QUOTE_SET} returns the order to {@code AWAITING_PAYMENT}, {@code QUOTE_DECLINED} cancels it, and
 * the machine refuses either, as a conflict, for an order that is not awaiting a quote. The
 * human-decided price is written as the order's delivery quote, which is where {@code
 * Order.totals()} reads the delivery cost, so the total the customer is asked to pay already
 * contains it.
 *
 * <p>The quote is marked with {@link #MANAGER_TIER} instead of a tier id, because no tier priced
 * it. The terms text and the validity are kept in their own collection; nothing yet stops a
 * customer paying after {@code validUntil}, which is for the payment step to enforce.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

  /** The tier id recorded on a delivery quote that staff, not a tier, decided. */
  public static final String MANAGER_TIER = "manager-quote";

  /** The longest reason accepted for declining a quote. */
  public static final int REASON_MAX = 500;

  private final OrderStore store;
  private final OrderAdminService orderService;
  private final ManagerQuoteRepository quotes;
  private final ManagerQuoteMapper quoteMapper;
  private final CustomerRepository customers;
  private final QuoteMails mails;
  private final Clock clock;

  /**
   * Lists the orders awaiting a quote, newest first.
   *
   * @param page the zero-based page
   * @param size the page size, 1 to 100
   * @return the page
   */
  public PageView<OrderSummaryView> awaiting(int page, int size) {
    return orderService.list(OrderStatus.AWAITING_MANAGER_QUOTE, page, size);
  }

  /**
   * Shows one order, as staff need it to decide.
   *
   * @param orderId the order id
   * @return the order in full
   */
  public OrderDetailView detail(String orderId) {
    return orderService.detail(orderId);
  }

  /**
   * Sets the terms: the order returns to awaiting payment with the staff-decided delivery price.
   *
   * @param orderId the order id
   * @param request the price, terms and validity
   * @param staff who is acting
   * @return the order's new status and what the customer will pay
   * @throws ValidationException if the request is incomplete or a value is out of range
   */
  public QuoteOutcome applyTerms(
      String orderId, ManagerQuoteRequest request, StaffPrincipal staff) {
    if (request == null || request.deliveryPrice() == null || request.validUntil() == null) {
      throw new ValidationException(
          "quote.incomplete", "Terms need a delivery price, terms text and a validity");
    }
    final Order before = store.require(orderId);
    final Instant now = clock.instant();
    if (!request.validUntil().isAfter(now)) {
      throw new ValidationException(
          "quote.validity-invalid", "The offer must be valid in the future");
    }
    final Currency currency = before.lines().get(0).lineNet().currency();
    final ManagerQuote decided =
        new ManagerQuote(Money.of(request.deliveryPrice(), currency), request.terms(), now);
    final DeliveryQuote delivery =
        new DeliveryQuote(
            new FulfillmentTierId(MANAGER_TIER),
            decided.finalPrice(),
            new TransitTime(request.transitMinDays(), request.transitMaxDays()),
            now);
    final Order after =
        OrderTransitions.withQuote(
            OrderTransitions.advance(before, TransitionTrigger.QUOTE_SET, now), delivery);
    store.advance(before, after);
    quotes.save(quoteMapper.toDocument(after.id(), decided, request.validUntil(), staff.email()));
    final CustomerDocument customer = customers.findById(after.customerId().value()).orElse(null);
    final String number = after.number().format();
    if (customer == null) {
      log.warn("Order {} has no customer record; the terms mail was not sent", number);
    } else {
      mails.announce(after, customer, decided, request.validUntil());
    }
    final OrderTotals totals = after.totals();
    return new QuoteOutcome(
        after.id().value(),
        number,
        after.status(),
        delivery.cost().amount().toPlainString(),
        totals.grandTotal().amount().toPlainString(),
        currency.code());
  }

  /**
   * Declines the quote: the order is cancelled.
   *
   * @param orderId the order id
   * @param reason why, or null if none was given
   * @param staff who is acting
   * @return the order's new status
   * @throws ValidationException if the reason is longer than {@value #REASON_MAX} characters
   */
  public QuoteOutcome decline(String orderId, String reason, StaffPrincipal staff) {
    if (reason != null && reason.length() > REASON_MAX) {
      throw new ValidationException(
          "quote.reason-too-long", "The reason must be at most " + REASON_MAX + " characters");
    }
    final Order before = store.require(orderId);
    final Order after =
        OrderTransitions.advance(before, TransitionTrigger.QUOTE_DECLINED, clock.instant());
    store.advance(before, after);
    final String number = after.number().format();
    final String who = staff.email();
    final String why =
        reason == null ? "no reason given" : reason.replaceAll("\\p{Cntrl}", " ").strip();
    log.info("Quote for order {} declined by {}: {}", number, who, why);
    return new QuoteOutcome(after.id().value(), number, after.status(), null, null, null);
  }
}
