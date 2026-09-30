/**
 * The HTTP conventions every endpoint of {@code services/api} shares: the one error envelope and
 * the handler that produces it. There is exactly one envelope; a later task that needs a new kind
 * of error adds a code, not a second body shape.
 */
package com.rednavis.metaldesk.api.web;
