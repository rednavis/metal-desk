/**
 * The customer side of the domain model (Architecture section 3): {@link
 * com.rednavis.metaldesk.share.domain.customer.Customer} with its {@link
 * com.rednavis.metaldesk.share.domain.customer.Address}es, and the {@link
 * com.rednavis.metaldesk.share.domain.customer.AuthCredential} used to sign in.
 *
 * <p><strong>Credential and customer are one-to-many, not one-to-one</strong> (BRD FR-2.5): one
 * authenticated principal can switch between several accounts without a full re-login, so an {@code
 * AuthCredential} carries no {@code CustomerId} and a {@code Customer} carries no password. The
 * mapping between them is owned by the auth service (tasks T-032, T-033).
 *
 * <p>Value types here ({@link com.rednavis.metaldesk.share.domain.customer.EmailAddress}, {@link
 * com.rednavis.metaldesk.share.domain.customer.PhoneNumber}) validate and normalise at
 * construction, so downstream code never re-checks them and equality means "the same address".
 */
package com.rednavis.metaldesk.share.domain.customer;
