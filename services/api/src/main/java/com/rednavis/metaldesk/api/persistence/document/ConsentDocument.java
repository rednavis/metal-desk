package com.rednavis.metaldesk.api.persistence.document;

import java.time.Instant;

/**
 * The privacy-policy acceptance recorded on a checkout session (BRD FR-4.3). It is one consent and
 * only that one: there is no field that stands for several.
 *
 * @param privacyPolicyAccepted whether the customer accepted the privacy policy
 * @param policyVersion which version of the policy they accepted
 * @param acceptedAt when they accepted it
 */
public record ConsentDocument(
    boolean privacyPolicyAccepted, String policyVersion, Instant acceptedAt) {}
