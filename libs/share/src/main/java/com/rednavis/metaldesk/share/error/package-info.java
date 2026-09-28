/**
 * The shared domain-failure taxonomy: {@link com.rednavis.metaldesk.share.error.DomainException}
 * and exactly three kinds — validation, not-found and conflict — that every service maps to an HTTP
 * error envelope (BRD FR-8.1). The set is closed on purpose; see {@code DomainException}.
 */
package com.rednavis.metaldesk.share.error;
