package com.rednavis.metaldesk.api.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The one place a password is hashed or checked, always <strong>off the event loop</strong>
 * (Architecture section 5).
 *
 * <p>A password hash is deliberately expensive, tens of milliseconds of CPU. Run on an event-loop
 * thread it would stall every other request that thread is serving, and it does so invisibly: it
 * passes every functional test with one user and only degrades under load. So both operations here
 * are wrapped to run on the bounded-elastic scheduler, and no caller decides that for itself.
 *
 * <p>{@link #dummyHash()} exists so that a sign-in for an identifier nobody owns can still run a
 * full verification against a real hash. Without it the "no such user" path would be measurably
 * faster than "wrong password", and the response time would reveal which accounts exist (BRD
 * FR-2.2). Do not remove it as dead code.
 */
@Component
public class PasswordEncoderAdapter {

  /** BCrypt reads only the first 72 bytes; a longer password is rejected, not silently cut. */
  private static final int MAX_BYTES = 72;

  private final PasswordEncoder encoder;
  private final String unknownHash;

  /**
   * Creates the adapter and the hash used for unknown identifiers.
   *
   * @param encoder the password encoder doing the real work
   */
  public PasswordEncoderAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
    this.unknownHash = encoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Hashes a password.
   *
   * @param password the plaintext password
   * @return the encoded hash, computed on the bounded-elastic scheduler
   */
  public Mono<String> encode(String password) {
    return Mono.fromCallable(() -> encoder.encode(password))
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Checks a password against a hash.
   *
   * @param password the plaintext password; null, blank or over-long never matches
   * @param hash the encoded hash to check against
   * @return whether it matches, computed on the bounded-elastic scheduler; a password that is
   *     unusable still costs a full hash check, so it takes as long as any other
   */
  public Mono<Boolean> matches(String password, String hash) {
    return Mono.fromCallable(() -> check(password, hash)).subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * A valid hash of a random password nobody knows, to check against when there is no real one.
   *
   * @return the hash
   */
  public String dummyHash() {
    return unknownHash;
  }

  private boolean check(String password, String hash) {
    final boolean usable =
        password != null
            && !password.isBlank()
            && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    final boolean matched = encoder.matches(usable ? password : "unusable", hash);
    return usable && matched;
  }
}
