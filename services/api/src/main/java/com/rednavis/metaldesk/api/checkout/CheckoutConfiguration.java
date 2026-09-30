package com.rednavis.metaldesk.api.checkout;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the checkout's externally configured settings. */
@Configuration
@EnableConfigurationProperties(CheckoutProperties.class)
public class CheckoutConfiguration {}
