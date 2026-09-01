package com.rednavis.metaldesk.api.web;

import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints that fail in each way, for testing {@link DomainExceptionHandler}. */
@RestController
public class FailingController {

  /** Fails validation. */
  @GetMapping("/validation")
  public String validation() {
    throw new ValidationException("thing.invalid", "bad thing");
  }

  /** Finds nothing. */
  @GetMapping("/missing")
  public String missing() {
    throw new NotFoundException("thing.not-found", "no thing");
  }

  /** Conflicts. */
  @GetMapping("/conflict")
  public String conflict() {
    throw new ConflictException("thing.conflict", "clash");
  }

  /** Fails unexpectedly. */
  @GetMapping("/boom")
  public String boom() {
    throw new IllegalStateException("secret detail");
  }

  /** Echoes a number. */
  @GetMapping("/number")
  public String number(@RequestParam("n") int value) {
    return String.valueOf(value);
  }
}
