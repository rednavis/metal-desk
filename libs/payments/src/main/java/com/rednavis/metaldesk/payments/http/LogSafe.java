package com.rednavis.metaldesk.payments.http;

/**
 * Makes values that came from outside safe to write to a log line. A provider's decline code is
 * outside input: unescaped, it could break a log entry in two or bury another line in it.
 */
public final class LogSafe {

  private static final int MAX_LENGTH = 40;

  private LogSafe() {}

  /**
   * Reduces a value to letters, digits, underscore and hyphen, replacing anything else with a
   * question mark, and shortens it.
   *
   * @param value the outside value, possibly null
   * @return a short, single-line rendering, {@code (none)} for null
   */
  public static String code(String value) {
    final String cleaned = value == null ? "(none)" : value.replaceAll("[^A-Za-z0-9_-]", "?");
    return cleaned.substring(0, Math.min(cleaned.length(), MAX_LENGTH));
  }
}
