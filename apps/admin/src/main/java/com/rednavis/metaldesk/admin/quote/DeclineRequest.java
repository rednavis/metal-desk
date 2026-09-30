package com.rednavis.metaldesk.admin.quote;

/**
 * Why a manager quote is declined.
 *
 * @param reason the reason, at most {@value QuoteService#REASON_MAX} characters; it is recorded in
 *     the audit log beside who declined
 */
public record DeclineRequest(String reason) {}
