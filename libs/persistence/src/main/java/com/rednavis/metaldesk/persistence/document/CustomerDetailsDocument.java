package com.rednavis.metaldesk.persistence.document;

/**
 * The customer and delivery data of checkout step 1, as stored on a checkout session (BRD FR-4.1).
 *
 * @param name the customer's name
 * @param email the normalised email address
 * @param phone the normalised phone number
 * @param address the delivery address, with the optional company details
 * @param note the optional order note, or null
 */
public record CustomerDetailsDocument(
    String name, String email, String phone, AddressDocument address, String note) {}
