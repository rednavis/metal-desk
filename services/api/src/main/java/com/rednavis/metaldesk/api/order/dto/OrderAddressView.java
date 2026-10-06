package com.rednavis.metaldesk.api.order.dto;

/**
 * Where an order is delivered.
 *
 * @param street the street and number
 * @param postalCode the postal code
 * @param city the city
 * @param country the ISO country code
 */
public record OrderAddressView(String street, String postalCode, String city, String country) {}
