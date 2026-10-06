package com.rednavis.metaldesk.payments.wallet;

/**
 * What the wallet provider answers, as parsed from the wire. Every field is optional on the wire,
 * so all are nullable here; {@link WalletProvider} decides what a combination means and treats a
 * missing field it needs as a malformed response. Fields the adapter does not know are ignored and
 * never kept.
 *
 * @param status what happened: {@code captured}, {@code declined} or {@code failed}
 * @param reference the wallet provider's handle for the payment
 * @param declineCode the provider's own code for why it declined, for {@code declined}
 */
record WalletResponse(String status, String reference, String declineCode) {}
