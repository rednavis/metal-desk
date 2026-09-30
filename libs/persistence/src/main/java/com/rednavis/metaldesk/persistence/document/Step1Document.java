package com.rednavis.metaldesk.persistence.document;

/**
 * What checkout step 1 collected, as stored on a checkout session.
 *
 * @param details the customer and delivery data, or null before step 1
 * @param consent the privacy acceptance, or null before step 1
 * @param conversion the guest's quick registration, or null
 */
public record Step1Document(
    CustomerDetailsDocument details, ConsentDocument consent, ConversionDocument conversion) {}
