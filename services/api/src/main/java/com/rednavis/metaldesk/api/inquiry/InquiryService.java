package com.rednavis.metaldesk.api.inquiry;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.inquiry.dto.InquiryReceipt;
import com.rednavis.metaldesk.api.inquiry.dto.InquiryRequest;
import com.rednavis.metaldesk.api.persistence.document.InquiryDocument;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.InquiryRepository;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.api.web.FieldViolation;
import com.rednavis.metaldesk.api.web.FieldViolationsException;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Takes a price inquiry or a free-form message for staff (BRD FR-9.1, FR-9.2).
 *
 * <p>There are three sources and one contract: the inquiry is stored, the customer gets a receipt
 * with the reference and staff get the message, whichever source it came from. Nobody has to be
 * signed in.
 *
 * <p>An inquiry about a handed-off order must name that order <em>and</em> the email it was placed
 * with. A reference that does not exist, is not a handoff, or is someone else's all fail the same
 * way, so the endpoint cannot be used to find out which order numbers exist.
 *
 * <p>The inquiry is stored before any mail, and a failed mail is logged at error level with the
 * reference rather than failing the request: the customer would only resend it, and staff would
 * then read it twice.
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

  private static final String REFERENCE_PREFIX = "INQ-";
  private static final int REFERENCE_LENGTH = 10;
  private static final String UNKNOWN = "unknown";

  private final InquiryRepository inquiries;
  private final ProductRepository products;
  private final OrderRepository orders;
  private final CustomerRepository customers;
  private final InquiryNotifications notifications;
  private final Clock clock;

  /**
   * Records an inquiry and notifies both parties.
   *
   * @param request what the sender wrote
   * @return the reference and what happens next
   * @throws FieldViolationsException if the request, or the product or order it names, is invalid
   */
  public Mono<InquiryReceipt> submit(InquiryRequest request) {
    final EmailAddress email = InquiryValidator.validate(request);
    return context(request, email)
        .map(context -> inquiry(request, email, context))
        .flatMap(this::store)
        .flatMap(
            inquiry ->
                notifications
                    .send(inquiry)
                    .thenReturn(
                        new InquiryReceipt(
                            inquiry.reference(),
                            "Thank you. We have your message and will reply by email.")));
  }

  /** The product or order the inquiry is about, checked to exist; empty for a catalog inquiry. */
  private Mono<Optional<String>> context(InquiryRequest request, EmailAddress email) {
    return switch (request.source()) {
      case CATALOG -> Mono.just(Optional.empty());
      case PRODUCT ->
          products
              .existsById(request.productId().strip())
              .filter(exists -> exists)
              .switchIfEmpty(Mono.error(() -> invalid("productId")))
              .thenReturn(Optional.of(request.productId().strip()));
      case HANDOFF ->
          orders
              .findByNumber(request.handoffReference().strip())
              .filter(order -> order.status() == OrderStatus.AWAITING_MANAGER_QUOTE)
              .flatMap(
                  order ->
                      customers
                          .findById(order.customerId())
                          .filter(owner -> owner.email().equalsIgnoreCase(email.value()))
                          .map(owner -> order.number()))
              .switchIfEmpty(Mono.error(() -> invalid("handoffReference")))
              .map(Optional::of);
    };
  }

  private Inquiry inquiry(InquiryRequest request, EmailAddress email, Optional<String> context) {
    return new Inquiry(
        REFERENCE_PREFIX
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, REFERENCE_LENGTH)
                .toUpperCase(Locale.ROOT),
        request.source(),
        request.name().strip(),
        email,
        request.topic().strip(),
        request.message().strip(),
        request.source() == InquirySource.PRODUCT ? context : Optional.empty(),
        request.source() == InquirySource.HANDOFF ? context : Optional.empty(),
        LocaleParser.parse(request.locale()),
        clock.instant());
  }

  private Mono<Inquiry> store(Inquiry inquiry) {
    return inquiries
        .insert(
            new InquiryDocument(
                UUID.randomUUID().toString(),
                inquiry.reference(),
                inquiry.source().name(),
                inquiry.name(),
                inquiry.email().value(),
                inquiry.topic(),
                inquiry.message(),
                inquiry.productId().orElse(null),
                inquiry.orderNumber().orElse(null),
                inquiry.locale().toLanguageTag(),
                inquiry.createdAt()))
        .thenReturn(inquiry);
  }

  private static FieldViolationsException invalid(String field) {
    return new FieldViolationsException(
        List.of(new FieldViolation(field, UNKNOWN, "No such " + field + " for this inquiry")));
  }
}
