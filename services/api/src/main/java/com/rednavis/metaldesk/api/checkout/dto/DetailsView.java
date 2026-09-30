package com.rednavis.metaldesk.api.checkout.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The customer and delivery data of step 1 as stored, echoed back so the form can be shown again
 * (BRD FR-7.1: any earlier step can be edited).
 *
 * @param name the customer's name
 * @param email the normalised email address
 * @param phone the normalised phone number
 * @param street the street line
 * @param city the city
 * @param country the country code
 * @param postalCode the postal code
 * @param companyName the company name, if any
 * @param companyAddress the company's address, if any
 * @param note the order note, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DetailsView(
    String name,
    String email,
    String phone,
    String street,
    String city,
    String country,
    String postalCode,
    String companyName,
    String companyAddress,
    String note) {}
