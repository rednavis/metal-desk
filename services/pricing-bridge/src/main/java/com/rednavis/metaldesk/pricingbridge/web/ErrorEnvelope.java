package com.rednavis.metaldesk.pricingbridge.web;

/**
 * The error body every service of the platform uses, the same as {@code services/api}'s.
 *
 * @param code the stable machine-readable code
 * @param message the human-readable explanation
 * @param correlationId the caller's correlation id, or a generated one
 */
public record ErrorEnvelope(String code, String message, String correlationId) {}
