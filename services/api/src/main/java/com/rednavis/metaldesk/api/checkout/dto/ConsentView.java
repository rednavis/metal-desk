package com.rednavis.metaldesk.api.checkout.dto;

import java.time.Instant;

/**
 * The recorded privacy acceptance.
 *
 * @param policyVersion the version accepted
 * @param acceptedAt when it was accepted
 */
public record ConsentView(String policyVersion, Instant acceptedAt) {}
