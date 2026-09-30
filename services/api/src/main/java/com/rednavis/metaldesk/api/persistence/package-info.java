/**
 * The persistence layer of {@code services/api}: reactive repositories over the documents of {@code
 * libs/persistence}, and the atomic order-number sequence (Architecture section 5, {@code
 * controller -> service -> repository}).
 *
 * <p>The documents and the hand-written mappers between them and the domain live in {@code
 * libs/persistence}, shared with {@code apps/admin}; see its package documentation for the mapping
 * boundary and the storage conventions. What stays here is what depends on the execution model:
 * nothing in this package blocks, repositories return {@code Mono} and {@code Flux}, and only the
 * reactive-streams MongoDB driver is on the classpath.
 */
package com.rednavis.metaldesk.api.persistence;
