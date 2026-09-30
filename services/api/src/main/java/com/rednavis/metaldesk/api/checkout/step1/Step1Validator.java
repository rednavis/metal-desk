package com.rednavis.metaldesk.api.checkout.step1;

import com.rednavis.metaldesk.api.account.PasswordPolicy;
import com.rednavis.metaldesk.api.checkout.dto.Step1Request;
import com.rednavis.metaldesk.api.web.FieldViolation;
import com.rednavis.metaldesk.api.web.FieldViolationsException;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Validates checkout step 1 (BRD FR-4.1, FR-4.3): every mandatory field for presence <em>and</em>
 * format, the optional fields for length, the privacy acceptance, and, for a guest who wants an
 * account, the password.
 *
 * <p><strong>All violations are reported, not the first.</strong> A form binds to this: one round
 * trip names every mistake. Presence and format are separate codes ({@code required} against {@code
 * format}), because a missing city and a malformed postal code are different things to tell a
 * customer.
 *
 * <p>The country must be an ISO 3166 two-letter code and so resolve to a {@link Region}; anything
 * else is a field error saying delivery is unavailable there, not a server error. The postal-code
 * format is deliberately generic (letters, digits, spaces and dashes, 3 to 11 characters): a
 * per-country format table is not asked for.
 */
public final class Step1Validator {

  private static final int NAME_MAX = 120;
  private static final int ADDRESS_MAX = 200;
  private static final int NOTE_MAX = 500;
  private static final Pattern POSTAL_CODE =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9 -]{1,9}[A-Za-z0-9]");
  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

  private static final String REQUIRED = "required";
  private static final String FORMAT = "format";
  private static final String TOO_LONG = "too-long";

  private Step1Validator() {}

  /**
   * The outcome of a valid step 1.
   *
   * @param details the customer and delivery data
   * @param consent the privacy acceptance
   * @param password the new account's password if a guest asked for an account, else empty
   */
  public record Validated(
      CustomerDetails details, ConsentRecord consent, Optional<String> password) {}

  /**
   * Validates a request.
   *
   * @param request the submitted step 1; a missing body fails every mandatory field
   * @param guest whether the request is from a guest, who alone can ask for an account
   * @param now the time to record on the consent
   * @param policy the privacy policy version that must be the one accepted
   * @return the validated data
   * @throws FieldViolationsException listing every invalid field
   */
  public static Validated validate(
      Step1Request request, boolean guest, Instant now, String policy) {
    final Step1Request form = request == null ? emptyRequest() : request;
    final List<FieldViolation> violations = new ArrayList<>();
    final Step1Request.Contact contact =
        form.contact() == null ? new Step1Request.Contact(null, null) : form.contact();
    final Step1Request.Company company =
        form.company() == null ? new Step1Request.Company(null, null) : form.company();
    final String name = mandatory(violations, "name", form.name(), NAME_MAX);
    final EmailAddress email =
        parse("contact.email", contact.email(), violations, EmailAddress::new);
    final PhoneNumber phone = parse("contact.phone", contact.phone(), violations, PhoneNumber::new);
    final String street = mandatory(violations, "street", form.street(), NAME_MAX);
    final String city = mandatory(violations, "city", form.city(), NAME_MAX);
    final Region country = country(violations, form.country());
    final String postalCode = postalCode(violations, form.postalCode());
    final String companyName = optional(violations, "company.name", company.name(), NAME_MAX);
    final String companyAddress =
        optional(violations, "company.address", company.address(), ADDRESS_MAX);
    final String note = optional(violations, "note", form.note(), NOTE_MAX);
    final String policyVersion = policy(violations, form, policy);
    final Optional<String> password = password(violations, form, guest);
    if (!violations.isEmpty()) {
      throw new FieldViolationsException(violations);
    }
    final Address address =
        new Address(
            AddressKind.DELIVERY, street, city, country, postalCode, companyName, companyAddress);
    return new Validated(
        new CustomerDetails(name, email, phone, address, Optional.ofNullable(note)),
        new ConsentRecord(true, policyVersion, now),
        password);
  }

  private static Step1Request emptyRequest() {
    return new Step1Request(null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static String mandatory(
      List<FieldViolation> violations, String field, String value, int max) {
    String result = null;
    if (value == null || value.isBlank()) {
      violations.add(new FieldViolation(field, REQUIRED, field + " is required"));
    } else if (value.strip().length() > max) {
      violations.add(
          new FieldViolation(field, TOO_LONG, field + " must be at most " + max + " characters"));
    } else {
      result = value.strip();
    }
    return result;
  }

  private static String optional(
      List<FieldViolation> violations, String field, String value, int max) {
    String result = null;
    if (value != null && !value.isBlank()) {
      if (value.strip().length() > max) {
        violations.add(
            new FieldViolation(field, TOO_LONG, field + " must be at most " + max + " characters"));
      } else {
        result = value.strip();
      }
    }
    return result;
  }

  private static <T> T parse(
      String field, String value, List<FieldViolation> violations, Function<String, T> factory) {
    T result = null;
    if (value == null || value.isBlank()) {
      violations.add(new FieldViolation(field, REQUIRED, field + " is required"));
    } else {
      try {
        result = factory.apply(value);
      } catch (ValidationException malformed) {
        violations.add(new FieldViolation(field, FORMAT, "That is not a valid " + field));
      }
    }
    return result;
  }

  private static Region country(List<FieldViolation> violations, String value) {
    Region region = null;
    if (value == null || value.isBlank()) {
      violations.add(new FieldViolation("country", REQUIRED, "country is required"));
    } else if (COUNTRIES.contains(value.strip().toUpperCase(Locale.ROOT))) {
      region = new Region(value);
    } else {
      violations.add(
          new FieldViolation(
              "country",
              "unsupported",
              "Delivery is not available for that country; use a two-letter country code"));
    }
    return region;
  }

  private static String postalCode(List<FieldViolation> violations, String value) {
    String result = null;
    if (value == null || value.isBlank()) {
      violations.add(new FieldViolation("postalCode", REQUIRED, "postalCode is required"));
    } else if (POSTAL_CODE.matcher(value.strip()).matches()) {
      result = value.strip();
    } else {
      violations.add(new FieldViolation("postalCode", FORMAT, "That is not a valid postal code"));
    }
    return result;
  }

  private static String policy(
      List<FieldViolation> violations, Step1Request form, String currentVersion) {
    String version = null;
    if (Boolean.TRUE.equals(form.privacyPolicyAccepted())) {
      if (form.policyVersion() == null || form.policyVersion().isBlank()) {
        violations.add(
            new FieldViolation(
                "policyVersion", REQUIRED, "The accepted policy version is required"));
      } else if (form.policyVersion().strip().equals(currentVersion)) {
        version = currentVersion;
      } else {
        violations.add(
            new FieldViolation(
                "policyVersion",
                "consent.version-mismatch",
                "The privacy policy has changed; review and accept the current version"));
      }
    } else {
      violations.add(
          new FieldViolation(
              "privacyPolicyAccepted",
              "consent.privacy-required",
              "The privacy policy must be accepted to continue"));
    }
    return version;
  }

  private static Optional<String> password(
      List<FieldViolation> violations, Step1Request form, boolean guest) {
    Optional<String> password = Optional.empty();
    final Step1Request.Account account = form.account();
    if (guest && account != null && account.rememberMe()) {
      if (account.password() == null || account.password().isBlank()) {
        violations.add(new FieldViolation("account.password", REQUIRED, "A password is required"));
      } else {
        try {
          password = Optional.of(PasswordPolicy.require(account.password()));
        } catch (ValidationException weak) {
          violations.add(
              new FieldViolation("account.password", "password.invalid", weak.getMessage()));
        }
      }
    }
    return password;
  }
}
