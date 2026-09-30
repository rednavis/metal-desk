package com.rednavis.metaldesk.api.web;

/**
 * The body of every error response (BRD FR-8.1: errors must be support-actionable).
 *
 * <p>{@code code} is the stable, machine-readable code a client switches on and support searches
 * for; {@code message} is for a human and may change; {@code correlationId} ties the response to
 * the server's log line, and is the request's {@code X-Correlation-Id} header when the caller sent
 * one. Nothing else is ever in it: no stack trace, no class name, no internal detail.
 *
 * @param code the machine-readable error code, for example {@code product.not-found}
 * @param message a human-readable description
 * @param correlationId the id to quote to support
 */
public record ApiErrorEnvelope(String code, String message, String correlationId) {}
