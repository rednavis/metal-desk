package com.rednavis.metaldesk.api.account.dto;

/**
 * Asks to act as another account (BRD FR-2.5).
 *
 * @param targetCustomerId the customer id to switch to
 */
public record SwitchRequest(String targetCustomerId) {}
