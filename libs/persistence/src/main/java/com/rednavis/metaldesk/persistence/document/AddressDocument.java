package com.rednavis.metaldesk.persistence.document;

import com.rednavis.metaldesk.share.domain.customer.AddressKind;

/**
 * A postal address as stored, embedded in a customer or an order.
 *
 * @param kind whether it is a delivery or a billing address
 * @param street the street line
 * @param city the city
 * @param country the country's region code
 * @param postalCode the postal code
 * @param companyName the company name, or null for a private address
 * @param companyAddress the company's registered address, or null
 */
public record AddressDocument(
    AddressKind kind,
    String street,
    String city,
    String country,
    String postalCode,
    String companyName,
    String companyAddress) {}
