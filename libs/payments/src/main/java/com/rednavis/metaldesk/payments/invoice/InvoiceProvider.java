package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.provider.ProviderCapability;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethodGroup;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import reactor.core.publisher.Mono;

/**
 * The {@link PaymentProvider} for paying by invoice (BRD FR-6.2, Architecture section 4). It makes
 * <strong>no outbound call at all</strong>: it reads the order through {@link InvoiceOrders},
 * renders the invoice through {@link InvoiceRenderer}, hands the documents to {@link InvoiceSink}
 * and reports {@link PaymentOutcome.DocumentIssued}. It has no HTTP client and no address, and a
 * test asserts both.
 *
 * <p><strong>Invoice is a first-class payment method, not a fallback.</strong> BRD BR-9 disables
 * gateway-processed methods above a configured order-value ceiling, which makes invoice the only
 * path for high-value orders. That is the significance of this provider; it is not a legacy path.
 *
 * <p><strong>An invoice is a promise to pay, not a capture.</strong> The outcome is {@code
 * DocumentIssued}, whose status is {@code PENDING}, never {@code Captured}. Reporting it as
 * captured would mark an unpaid order paid and let it leave {@code AWAITING_PAYMENT}.
 *
 * <p><strong>One or two documents.</strong> The lines are split by the tax category snapshotted on
 * each line ({@link InvoiceSplitter}); both documents share one {@link InvoiceNumber}, which is the
 * payment's reference. The delivery cost is on the first document, so their totals add up to the
 * order's.
 *
 * <p><strong>The locale is an input.</strong> Documents are rendered in the locale of the {@link
 * PaymentIntent}. Nothing here reads a default locale or any thread-local.
 *
 * <p>Sending the emails is not this provider's job ({@code libs/mail}, wired in T-038). The
 * documents' totals are those of the order's snapshotted lines and delivery quote (BRD BR-5); a
 * price a manager set for a handoff order (BRD FR-5.3) is not reflected here yet.
 */
public final class InvoiceProvider implements PaymentProvider {

  private static final String PROVIDER_ID = "invoice";

  private final InvoiceOrders orders;
  private final InvoiceRenderer renderer;
  private final InvoiceSink sink;
  private final ProviderCapability served;

  /**
   * Creates the provider.
   *
   * @param orders where the order being invoiced is read from
   * @param renderer draws each document
   * @param sink receives the rendered documents
   */
  public InvoiceProvider(InvoiceOrders orders, InvoiceRenderer renderer, InvoiceSink sink) {
    this.orders = orders;
    this.renderer = renderer;
    this.sink = sink;
    final Set<PaymentMethod> invoiceMethods =
        Arrays.stream(PaymentMethod.values())
            .filter(method -> method.group() == PaymentMethodGroup.INVOICE)
            .collect(Collectors.toSet());
    this.served = new ProviderCapability(PROVIDER_ID, invoiceMethods);
  }

  @Override
  public ProviderCapability capability() {
    return served;
  }

  /**
   * Issues the invoice for the intent's order.
   *
   * @param intent the payment being made by invoice
   * @return a {@code Mono} of {@link PaymentOutcome.DocumentIssued} carrying the invoice number; it
   *     errors with a {@code ValidationException} for a method other than invoice, and with a
   *     {@code NotFoundException} when the order does not exist. It never errors with a {@code
   *     PaymentProviderException}, because nothing is called.
   */
  @Override
  public Mono<PaymentOutcome> authorise(PaymentIntent intent) {
    return intent != null && supports(intent.method())
        ? orders
            .find(intent.orderId())
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new NotFoundException(
                            "invoice.order-not-found", "There is no order to invoice")))
            .flatMap(order -> issue(order, intent.locale()))
        : Mono.error(
            new ValidationException(
                "invoice.method-unsupported", "The invoice provider serves only invoices"));
  }

  /**
   * Reports an invoice that was issued earlier. An invoice is settled outside the checkout, so
   * there is nothing more to confirm: the answer is the same {@code DocumentIssued}, still pending.
   *
   * @param reference the invoice number an earlier outcome carried
   * @return a {@code Mono} of {@link PaymentOutcome.DocumentIssued}
   */
  @Override
  public Mono<PaymentOutcome> confirm(ProviderReference reference) {
    return reference != null
        ? Mono.just(new PaymentOutcome.DocumentIssued(reference))
        : Mono.error(
            new ValidationException("invoice.reference-missing", "Provider reference is required"));
  }

  private Mono<PaymentOutcome> issue(Order order, Locale locale) {
    final InvoiceNumber number = InvoiceNumber.forOrder(order.number());
    final List<InvoiceDocument> documents = render(order, number, locale);
    final PaymentOutcome issued = new PaymentOutcome.DocumentIssued(number.toReference());
    return sink.publish(number, documents).thenReturn(issued);
  }

  private List<InvoiceDocument> render(Order order, InvoiceNumber number, Locale locale) {
    final OrderTotals whole = order.totals();
    final List<DocumentScope> scopes = InvoiceSplitter.split(order.lines());
    return IntStream.range(0, scopes.size())
        .mapToObj(index -> renderOne(order, number, locale, whole, scopes, index))
        .toList();
  }

  private InvoiceDocument renderOne(
      Order order,
      InvoiceNumber number,
      Locale locale,
      OrderTotals whole,
      List<DocumentScope> scopes,
      int index) {
    final Money delivery = index == 0 ? whole.delivery() : Money.zero(whole.delivery().currency());
    final DocumentScope scope = scopes.get(index);
    final OrderTotals totals = OrderTotalsCalculator.compute(scope.lines(), delivery);
    return renderer.render(
        new InvoiceContent(
            number, order.number(), scope, totals, locale, index + 1, scopes.size()));
  }
}
