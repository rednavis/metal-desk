package com.rednavis.metaldesk.api.cart;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The cart settings, bound from {@code metaldesk.cart}.
 *
 * @param cookieMaxAge how long the browser keeps it
 * @param cookieSecure whether the cookie is sent over HTTPS only; a deployment behind TLS sets it
 */
@ConfigurationProperties("metaldesk.cart")
public record CartProperties(
    @DefaultValue("30d") Duration cookieMaxAge, @DefaultValue("false") boolean cookieSecure) {}
