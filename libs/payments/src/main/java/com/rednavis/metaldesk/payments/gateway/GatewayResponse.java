package com.rednavis.metaldesk.payments.gateway;

/**
 * What the gateway answers, as parsed from the wire. Every field is optional on the wire, so all
 * are nullable here; {@link GatewayProvider} decides what a given combination means and treats a
 * missing field it needs as a malformed response.
 *
 * <p>Fields the adapter does not know are ignored when parsing and are never kept, so nothing the
 * gateway adds can end up stored.
 *
 * @param status what happened: {@code captured}, {@code redirect_required}, {@code declined} or
 *     {@code failed}
 * @param reference the gateway's handle for the payment
 * @param redirectUrl where to send the customer, for {@code redirect_required}
 * @param declineCode the gateway's own code for why it declined, for {@code declined}
 */
record GatewayResponse(String status, String reference, String redirectUrl, String declineCode) {}
