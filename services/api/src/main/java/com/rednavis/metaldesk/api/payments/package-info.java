/**
 * The wiring of the concrete payment adapters of {@code libs/payments} into {@code services/api}.
 *
 * <p>This is the <em>only</em> place in {@code services/api} that names an adapter class. The
 * checkout code sees {@code PaymentProvider} beans and picks one by what it {@code supports}, so
 * swapping an adapter, or pointing one at a real vendor instead of the stub, is configuration and
 * never a change to checkout (BRD FR-6.1, Architecture section 4).
 */
package com.rednavis.metaldesk.api.payments;
