package com.rednavis.metaldesk.api.web;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Chooses the correlation id of a response: the caller's {@code X-Correlation-Id} when it is short
 * and made of letters, digits, dots, dashes and underscores, and a fresh random id otherwise, so a
 * caller cannot put arbitrary text into a response body or a log line.
 */
public final class CorrelationId {

  /** The request header the caller may supply an id in. */
  public static final String HEADER = "X-Correlation-Id";

  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private CorrelationId() {}

  /**
   * Chooses the id.
   *
   * @param supplied the caller's header value, or null
   * @return the id to use
   */
  public static String choose(String supplied) {
    return supplied != null && SAFE.matcher(supplied).matches()
        ? supplied
        : UUID.randomUUID().toString();
  }
}
