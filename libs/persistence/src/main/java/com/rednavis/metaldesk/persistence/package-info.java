/**
 * The storage shape of the platform, shared by {@code services/api} and {@code apps/admin}: the
 * MongoDB documents and the hand-written mappers between them and the {@code libs/share} domain.
 *
 * <p><strong>The mapping boundary is one-directional.</strong> The {@code document} types carry the
 * Spring Data annotations and live here; the domain types in {@code libs/share} know nothing of
 * them (Architecture section 8, enforced by {@code DomainBoundaryTest}). Each aggregate has a
 * hand-written mapper in {@code mapper} with {@code toDomain} and {@code toDocument}; there is no
 * reflection-based mapping, so a field added to an aggregate is a compile error here instead of a
 * silently dropped column.
 *
 * <p><strong>No repositories.</strong> A repository is an execution-model decision: {@code
 * services/api} is reactive and declares {@code Mono}/{@code Flux} repositories over these
 * documents, {@code apps/admin} is MVC on virtual threads and declares blocking ones. This module
 * has neither driver on its classpath.
 *
 * <p>Money, weight, purity, margin and tax rate are stored as decimal <em>strings</em>, because
 * Spring Data would otherwise write a {@code BigDecimal} as text anyway and a string round-trips
 * the scale exactly. The cost is that a range query on an amount is not possible in the database;
 * nothing needs one, since ceilings are evaluated by the domain. Instants are stored with
 * millisecond precision, MongoDB's date resolution.
 */
package com.rednavis.metaldesk.persistence;
