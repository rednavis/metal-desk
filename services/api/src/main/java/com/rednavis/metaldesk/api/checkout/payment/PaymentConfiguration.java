package com.rednavis.metaldesk.api.checkout.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the payment step's settings. */
@Configuration
@EnableConfigurationProperties(PaymentMethodPolicy.class)
public class PaymentConfiguration {}
