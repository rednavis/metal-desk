package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * A human-readable order number, {@code <date><daily-sequence>} (BRD BR-6).
 *
 * <p><strong>Format.</strong> The BRD's example is {@code 080220220004}: day 08, month 02, year
 * 2022, the 4th order of that day. So the date is {@code ddMMyyyy} (8 digits) and the sequence is
 * zero-padded to {@value #SEQUENCE_WIDTH} digits, concatenated with no separator: 12 digits in all.
 * The sequence starts at 1 and ends at {@value #MAX_SEQUENCE}, so a day holds at most 9999 orders;
 * a tenth-thousandth order in one day is refused rather than silently widening the number. The
 * number is monotonic within a day only, as BR-6 says; it does not sort chronologically across
 * days.
 *
 * <p><strong>This type does not allocate sequences.</strong> A daily sequence needs a transactional
 * counter, which is persistence (T-030); this type only represents and validates a number that
 * something else allocated. There is deliberately no {@code next} or {@code generate}.
 *
 * @param date the day the order was created, with a four-digit year
 * @param sequence the order's position within its day, from 1 to {@value #MAX_SEQUENCE}
 */
public record OrderNumber(LocalDate date, int sequence) {

  /** How many digits the sequence is zero-padded to. */
  public static final int SEQUENCE_WIDTH = 4;

  /** The highest sequence one day can hold. */
  public static final int MAX_SEQUENCE = 9999;

  private static final int DATE_WIDTH = 8;
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);

  /**
   * Validates the date and sequence.
   *
   * @throws ValidationException if the date is null or outside years 1000 to 9999, or the sequence
   *     is outside 1 to {@value #MAX_SEQUENCE}
   */
  public OrderNumber {
    if (date == null) {
      throw new ValidationException("order-number.date-missing", "Order date must not be null");
    }
    if (date.getYear() < 1000 || date.getYear() > 9999) {
      throw new ValidationException(
          "order-number.year-out-of-range", "Order year must have four digits, was " + date);
    }
    if (sequence < 1 || sequence > MAX_SEQUENCE) {
      throw new ValidationException(
          "order-number.sequence-out-of-range",
          "Order sequence must be 1 to " + MAX_SEQUENCE + ", was " + sequence);
    }
  }

  /**
   * Parses the 12-digit text form.
   *
   * @param text the text, for example {@code "080220220004"}
   * @return the order number, whose {@link #format()} equals the text
   * @throws ValidationException if the text is not exactly 12 digits, is not a real date, or has a
   *     sequence of 0000
   */
  public static OrderNumber parse(String text) {
    if (text == null || text.length() != DATE_WIDTH + SEQUENCE_WIDTH || !isDigits(text)) {
      throw new ValidationException(
          "order-number.malformed", "Order number must be 12 digits, was " + text);
    }
    try {
      final LocalDate date = LocalDate.parse(text.substring(0, DATE_WIDTH), DATE_FORMAT);
      return new OrderNumber(date, Integer.parseInt(text.substring(DATE_WIDTH)));
    } catch (DateTimeParseException e) {
      throw new ValidationException(
          "order-number.malformed", "Order number has no such date: " + text, e);
    }
  }

  /**
   * Formats as the 12-digit text form.
   *
   * @return the date as {@code ddMMyyyy} followed by the zero-padded sequence
   */
  public String format() {
    return DATE_FORMAT.format(date) + String.format("%0" + SEQUENCE_WIDTH + "d", sequence);
  }

  /**
   * Returns the 12-digit text form.
   *
   * @return the same text as {@link #format()}
   */
  @Override
  public String toString() {
    return format();
  }

  private static boolean isDigits(String text) {
    return text.chars().allMatch(c -> c >= '0' && c <= '9');
  }
}
