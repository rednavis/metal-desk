/**
 * The shared domain model (Architecture section 3). Every domain type — value types, identifiers
 * and aggregates — lives in this package tree inside {@code libs/share}, so that no service invents
 * its own copy of {@code Money} or passes a bare {@code String} where an identifier belongs.
 *
 * <p>No type outside {@code libs/share} may declare a class in this package tree; that rule is
 * enforced in CI by Modernization Plan task T-021, and is only documented here.
 */
package com.rednavis.metaldesk.share.domain;
