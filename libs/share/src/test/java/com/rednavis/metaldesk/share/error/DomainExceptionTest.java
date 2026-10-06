package com.rednavis.metaldesk.share.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

  @Test
  void eachKindExposesItsCodeAndMessage() {
    final DomainException validation = new ValidationException("v.code", "bad input");
    assertEquals("v.code", validation.code());
    assertEquals("bad input", validation.getMessage());

    final DomainException notFound = new NotFoundException("n.code", "no such order");
    assertEquals("n.code", notFound.code());
    assertEquals("no such order", notFound.getMessage());

    final DomainException conflict = new ConflictException("c.code", "order closed");
    assertEquals("c.code", conflict.code());
    assertEquals("order closed", conflict.getMessage());
  }

  @Test
  void kindsAreDistinguishedByTypeAlone() {
    assertEquals("validation", kind(new ValidationException("x", "m")));
    assertEquals("not-found", kind(new NotFoundException("x", "m")));
    assertEquals("conflict", kind(new ConflictException("x", "m")));
  }

  @Test
  void everyKindIsUnchecked() {
    final RuntimeException exception = new NotFoundException("x", "m");

    assertInstanceOf(DomainException.class, exception);
    assertThrows(
        ConflictException.class,
        () -> {
          throw new ConflictException("x", "m");
        });
  }

  private static String kind(DomainException exception) {
    return switch (exception) {
      case ValidationException _ -> "validation";
      case NotFoundException _ -> "not-found";
      case ConflictException _ -> "conflict";
      case DomainException _ -> "other";
    };
  }
}
