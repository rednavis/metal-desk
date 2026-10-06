package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.regex.Pattern;

/**
 * The identifier of an order's invoice, used as the payment's {@link ProviderReference} (BRD
 * FR-6.2).
 *
 * <p>An order has one invoice, made of one or two documents; the documents share this number and
 * are told apart by their position, so the payment needs a single reference whatever the split. The
 * number is derived from the order number, {@code INV-} followed by its twelve digits, so it needs
 * no counter of its own and cannot collide: allocating sequences is persistence (T-030), and the
 * order number already carries one.
 *
 * @param value the invoice number, {@code INV-} and twelve digits
 */
public record InvoiceNumber(String value) {

  private static final Pattern FORMAT = Pattern.compile("INV-[0-9]{12}");

  /**
   * Validates the format.
   *
   * @throws ValidationException if the value is not {@code INV-} and twelve digits
   */
  public InvoiceNumber {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new ValidationException(
          "invoice-number.malformed", "Invoice number must be INV- and twelve digits");
    }
  }

  /**
   * Derives the invoice number of an order.
   *
   * @param orderNumber the order's number
   * @return the invoice number
   * @throws ValidationException if the order number is null
   */
  public static InvoiceNumber forOrder(OrderNumber orderNumber) {
    if (orderNumber == null) {
      throw new ValidationException("invoice-number.order-missing", "Order number is required");
    }
    return new InvoiceNumber("INV-" + orderNumber.format());
  }

  /**
   * Returns the number as the reference recorded on the payment.
   *
   * @return the provider reference
   */
  public ProviderReference toReference() {
    return new ProviderReference(value);
  }
}
