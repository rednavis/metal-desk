/**
 * Order history (BRD FR-10.1, task T-038): the signed-in customer's orders as a read projection.
 *
 * <p>Everything shown comes from the order's own snapshot, never from current product prices (BRD
 * BR-2). Every status has a customer-facing label, the manager-handoff state included, and carrier
 * and tracking exist only from {@code SHIPPED} onward. Another customer's order is a 404.
 */
package com.rednavis.metaldesk.api.order;
