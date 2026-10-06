package com.rednavis.metaldesk.api.inquiry;

import com.rednavis.metaldesk.api.inquiry.dto.InquiryRequest;
import com.rednavis.metaldesk.api.web.FieldViolation;
import com.rednavis.metaldesk.api.web.FieldViolationsException;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks an inquiry's own fields, all at once, before anything is looked up. The sender may be an
 * unauthenticated visitor, so the email is validated, not read from a principal (BRD FR-9.1).
 */
public final class InquiryValidator {

  private static final int NAME_MAX = 120;
  private static final int TOPIC_MAX = 150;
  private static final int MESSAGE_MAX = 2000;
  private static final int ID_MAX = 64;

  private static final String REQUIRED = "required";

  private InquiryValidator() {}

  /**
   * Validates a request.
   *
   * @param request the request; a missing body fails every mandatory field
   * @return the sender's email address, parsed
   * @throws FieldViolationsException listing every invalid field
   */
  public static EmailAddress validate(InquiryRequest request) {
    final List<FieldViolation> violations = new ArrayList<>();
    if (request == null) {
      throw new FieldViolationsException(
          List.of(new FieldViolation("source", REQUIRED, "An inquiry needs a source")));
    }
    if (request.source() == null) {
      violations.add(new FieldViolation("source", REQUIRED, "Say where the inquiry comes from"));
    }
    text(violations, "name", request.name(), NAME_MAX);
    text(violations, "topic", request.topic(), TOPIC_MAX);
    text(violations, "message", request.message(), MESSAGE_MAX);
    final EmailAddress email = email(violations, request.email());
    if (request.source() == InquirySource.PRODUCT) {
      text(violations, "productId", request.productId(), ID_MAX);
    }
    if (request.source() == InquirySource.HANDOFF) {
      text(violations, "handoffReference", request.handoffReference(), ID_MAX);
    }
    if (!violations.isEmpty()) {
      throw new FieldViolationsException(violations);
    }
    return email;
  }

  private static void text(List<FieldViolation> violations, String field, String value, int max) {
    if (value == null || value.isBlank()) {
      violations.add(new FieldViolation(field, REQUIRED, field + " is required"));
    } else if (value.strip().length() > max) {
      violations.add(
          new FieldViolation(field, "too-long", field + " must be at most " + max + " characters"));
    }
  }

  private static EmailAddress email(List<FieldViolation> violations, String value) {
    EmailAddress parsed = null;
    try {
      parsed = new EmailAddress(value);
    } catch (ValidationException invalid) {
      violations.add(new FieldViolation("email", "format", "Enter a valid email address"));
    }
    return parsed;
  }
}
