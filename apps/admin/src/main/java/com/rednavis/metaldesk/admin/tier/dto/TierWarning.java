package com.rednavis.metaldesk.admin.tier.dto;

/**
 * Something staff should know about a change that was nevertheless applied.
 *
 * @param code the stable code: {@code region.uncovered}, {@code region.narrowed} or {@code
 *     tier.shadowed}
 * @param message the explanation
 */
public record TierWarning(String code, String message) {}
