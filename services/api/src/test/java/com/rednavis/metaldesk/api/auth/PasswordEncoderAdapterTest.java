package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Architecture section 5: the hash never runs on an event-loop thread. */
class PasswordEncoderAdapterTest {

  private final BCryptPasswordEncoder real = new BCryptPasswordEncoder(4);

  @Test
  void verificationRunsOffTheEventLoop() {
    final AtomicBoolean nonBlocking = new AtomicBoolean(true);
    final AtomicReference<String> threadName = new AtomicReference<>();
    final PasswordEncoder recording =
        new PasswordEncoder() {
          @Override
          public String encode(CharSequence raw) {
            return real.encode(raw);
          }

          @Override
          public boolean matches(CharSequence raw, String encoded) {
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            threadName.set(Thread.currentThread().getName());
            return real.matches(raw, encoded);
          }
        };
    final PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(recording);
    final String hash = real.encode("secret-password");

    // Subscribing from a parallel (event-loop style, non-blocking) thread, as a request would.
    final Boolean matched =
        Mono.defer(() -> adapter.matches("secret-password", hash))
            .subscribeOn(Schedulers.parallel())
            .block();

    assertTrue(matched);
    assertFalse(nonBlocking.get(), "the KDF ran on a non-blocking thread");
    assertTrue(threadName.get().startsWith("boundedElastic"), threadName.get());
  }

  @Test
  void encodingAlsoRunsOffTheEventLoop() {
    final AtomicBoolean nonBlocking = new AtomicBoolean(true);
    final PasswordEncoder recording =
        new PasswordEncoder() {
          @Override
          public String encode(CharSequence raw) {
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            return real.encode(raw);
          }

          @Override
          public boolean matches(CharSequence raw, String encoded) {
            return real.matches(raw, encoded);
          }
        };
    final PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(recording);
    nonBlocking.set(true);

    adapter.encode("another-password").subscribeOn(Schedulers.parallel()).block();

    assertFalse(nonBlocking.get());
  }

  @Test
  void matchesTheRightPasswordAndNoOther() {
    final PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(real);
    final String hash = real.encode("right-password");

    assertTrue(adapter.matches("right-password", hash).block());
    assertFalse(adapter.matches("wrong-password", hash).block());
  }

  @Test
  void unusablePasswordsNeverMatchButStillCostHashCheck() {
    final int[] checks = {0};
    final PasswordEncoder counting =
        new PasswordEncoder() {
          @Override
          public String encode(CharSequence raw) {
            return real.encode(raw);
          }

          @Override
          public boolean matches(CharSequence raw, String encoded) {
            checks[0]++;
            return real.matches(raw, encoded);
          }
        };
    final PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(counting);

    assertFalse(adapter.matches(null, adapter.dummyHash()).block());
    assertFalse(adapter.matches("  ", adapter.dummyHash()).block());
    assertFalse(adapter.matches("x".repeat(100), adapter.dummyHash()).block());
    assertEquals(3, checks[0]);
  }
}
