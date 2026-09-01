package com.rednavis.metaldesk.api.web;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.regex.Pattern;

/** Checks the identifiers that arrive in request paths and bodies before they reach a query. */
public final class RequestIds {

  private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private RequestIds() {}

  /**
   * Requires a well-formed identifier.
   *
   * @param id the identifier
   * @param what what it identifies, for the message
   * @return the same identifier
   * @throws ValidationException with code {@code id.malformed} if it is null or malformed
   */
  public static String require(String id, String what) {
    if (id == null || !FORMAT.matcher(id).matches()) {
      throw new ValidationException("id.malformed", "Not a valid " + what + " id");
    }
    return id;
  }
}
