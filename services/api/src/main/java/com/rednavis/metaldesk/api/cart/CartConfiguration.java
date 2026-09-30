package com.rednavis.metaldesk.api.cart;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the cart's externally configured settings. */
@Configuration
@EnableConfigurationProperties(CartProperties.class)
public class CartConfiguration {}
