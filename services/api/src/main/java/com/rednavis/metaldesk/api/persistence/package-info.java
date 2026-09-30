/**
 * The persistence layer of {@code services/api}: how domain aggregates reach MongoDB and come back
 * (Architecture section 5, {@code controller -> service -> repository}).
 *
 * <p><strong>The mapping boundary is one-directional.</strong> The {@code document} types carry the
 * Spring Data annotations and live here; the domain types in {@code libs/share} know nothing of
 * them (Architecture section 8, enforced by {@code DomainBoundaryTest}). Each aggregate has a
 * hand-written mapper in {@code mapper} with {@code toDomain} and {@code toDocument}; there is no
 * reflection-based mapping, so a field added to an aggregate is a compile error here instead of a
 * silently dropped column.
 *
 * <p>Nothing in this package blocks: repositories return {@code Mono} and {@code Flux}, and only
 * the reactive-streams MongoDB driver is on the classpath.
 *
 * <p>Money, weight, purity, margin and tax rate are stored as decimal <em>strings</em>, because
 * Spring Data would otherwise write a {@code BigDecimal} as text anyway and a string round-trips
 * the scale exactly. The cost is that a range query on an amount is not possible in the database;
 * nothing needs one, since ceilings are evaluated by the domain. Instants are stored with
 * millisecond precision, MongoDB's date resolution.
 */
package com.rednavis.metaldesk.api.persistence;
