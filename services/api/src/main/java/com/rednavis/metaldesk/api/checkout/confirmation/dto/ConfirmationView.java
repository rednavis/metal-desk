package com.rednavis.metaldesk.api.checkout.confirmation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;

/**
 * The confirmation page of a placed order (BRD FR-8.1).
 *
 * <p>For a manager handoff there is no final total yet, so {@code total} is absent; the order
 * number is the reference the customer quotes, and it is the same number in every path.
 *
 * @param kind which way the order was placed
 * @param orderNumber the order number
 * @param status the order's status, by name
 * @param statusLabel the status as the customer reads it
 * @param itemCount the number of items
 * @param total the grand total, absent for a manager handoff
 * @param invoiceNumber the invoice number, for an invoice order
 * @param message what happens next
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfirmationView(
    ConfirmationKind kind,
    String orderNumber,
    String status,
    String statusLabel,
    int itemCount,
    PriceView total,
    String invoiceNumber,
    String message) {}
