package com.rednavis.metaldesk.api.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A saved delivery address, offered to pre-fill checkout (BRD FR-3.5). It is a suggestion: the form
 * stays editable and nothing may treat it as authoritative.
 *
 * @param street the street line
 * @param city the city
 * @param country the country's region code
 * @param postalCode the postal code
 * @param companyName the company name; absent for a private address
 * @param companyAddress the company's registered address; absent if none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressView(
    String street,
    String city,
    String country,
    String postalCode,
    String companyName,
    String companyAddress) {}
