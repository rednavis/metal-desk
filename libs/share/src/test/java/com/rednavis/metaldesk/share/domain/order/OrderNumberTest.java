package com.rednavis.metaldesk.share.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderNumberTest {

  private static final String BRD_EXAMPLE = "080220220004";

  @Test
  void formatMatchesTheBrdExample() {
    assertEquals(BRD_EXAMPLE, new OrderNumber(LocalDate.of(2022, 2, 8), 4).format());
  }

  @Test
  void parseReadsTheBrdExample() {
    final OrderNumber number = OrderNumber.parse(BRD_EXAMPLE);
    assertEquals(LocalDate.of(2022, 2, 8), number.date());
    assertEquals(4, number.sequence());
  }

  @ParameterizedTest
  @CsvSource({
    "2022-02-08,4",
    "2024-02-29,1",
    "2026-12-31,9999",
    "1000-01-01,1",
    "9999-12-31,5000",
    "2023-07-04,123"
  })
  void parseOfFormatRoundTripsForSampleSet(LocalDate date, int sequence) {
    final OrderNumber number = new OrderNumber(date, sequence);
    assertEquals(number, OrderNumber.parse(number.format()));
  }

  @Test
  void sequenceBoundsRoundTrip() {
    final LocalDate date = LocalDate.of(2026, 12, 31);
    assertEquals("311220260001", new OrderNumber(date, 1).format());
    assertEquals("311220269999", new OrderNumber(date, OrderNumber.MAX_SEQUENCE).format());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "abc",
        "08022022004",
        "0802202200045",
        "08022022000x",
        " 080220220004",
        "310220220001",
        "320120220001",
        "080220220000"
      })
  void malformedTextIsRefused(String text) {
    assertThrows(ValidationException.class, () -> OrderNumber.parse(text));
  }

  @Test
  void outOfRangeSequenceIsRefused() {
    final LocalDate date = LocalDate.of(2022, 2, 8);
    assertEquals(
        "order-number.sequence-out-of-range",
        assertThrows(ValidationException.class, () -> new OrderNumber(date, 0)).code());
    assertEquals(
        "order-number.sequence-out-of-range",
        assertThrows(
                ValidationException.class,
                () -> new OrderNumber(date, OrderNumber.MAX_SEQUENCE + 1))
            .code());
  }

  @Test
  void yearWithoutFourDigitsIsRefused() {
    assertEquals(
        "order-number.year-out-of-range",
        assertThrows(ValidationException.class, () -> new OrderNumber(LocalDate.of(999, 1, 1), 1))
            .code());
    assertEquals(
        "order-number.date-missing",
        assertThrows(ValidationException.class, () -> new OrderNumber(null, 1)).code());
  }

  @Test
  void exposesNoSequenceAllocatingMethod() {
    final boolean allocates =
        Arrays.stream(OrderNumber.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(
                name ->
                    name.contains("next")
                        || name.contains("generate")
                        || name.contains("allocate")
                        || name.contains("increment"));
    assertFalse(allocates);
  }

  @Test
  void stringFormEqualsFormat() {
    assertEquals(BRD_EXAMPLE, OrderNumber.parse(BRD_EXAMPLE).toString());
  }
}
