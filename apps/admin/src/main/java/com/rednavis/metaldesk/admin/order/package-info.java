/**
 * Order management for staff: listing and inspecting orders, and driving the statuses staff own,
 * {@code PAID -> FULFILLING -> SHIPPED -> DELIVERED}. Every status change is a trigger fired on the
 * state machine in {@code libs/share}; nothing here assigns a status.
 */
package com.rednavis.metaldesk.admin.order;
