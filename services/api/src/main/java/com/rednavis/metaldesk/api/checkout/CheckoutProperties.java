package com.rednavis.metaldesk.api.checkout;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The checkout settings, bound from {@code metaldesk.checkout}.
 *
 * @param policyVersion the version of the privacy policy a customer must accept; an acceptance of
 *     any other version is refused, so a stale acceptance cannot pass
 * @param sessionTtl how long an untouched checkout session lives before it is deleted
 * @param staffEmail where a manager handoff is announced
 * @param staffLocale the language of the staff notification
 * @param clientBaseUrl the storefront's checkout address; payment providers send the customer back
 *     to {@code <base>/<checkout id>/return} or {@code /cancel}
 */
@ConfigurationProperties("metaldesk.checkout")
public record CheckoutProperties(
    @DefaultValue("2026-10") String policyVersion,
    @DefaultValue("24h") Duration sessionTtl,
    @DefaultValue("staff@metal-desk.example.test") String staffEmail,
    @DefaultValue("en") String staffLocale,
    @DefaultValue("http://localhost:5173/checkout") String clientBaseUrl) {}
