package com.rednavis.metaldesk.api.inquiry.dto;

/**
 * The answer to an inquiry.
 *
 * @param reference the reference to quote in any later message
 * @param message what happens next
 */
public record InquiryReceipt(String reference, String message) {}
