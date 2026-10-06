package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.admin.persistence.UserRepository;
import com.rednavis.metaldesk.persistence.document.UserDocument;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Signs a back-office user in: finds the user by login, checks the password, issues the token.
 *
 * <p>Only the {@code users} collection is consulted, never the customers, so a customer's
 * credential cannot sign in here.
 *
 * <p><strong>One failure path.</strong> However an attempt fails, it becomes the single {@link
 * SignInOutcome.Rejected}. <strong>Constant-ish timing:</strong> when there is no usable user, the
 * password is still checked against a real hash nobody knows, so "no such user" costs about as much
 * as "wrong password" and the response time does not reveal which logins exist.
 */
@Service
public class AuthenticationService {

  /** BCrypt reads only the first 72 bytes; a longer password is rejected, not silently cut. */
  private static final int MAX_BYTES = 72;

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final SignInThrottle throttle;
  private final TokenIssuer issuer;
  private final String unknownHash;

  /**
   * Creates the service.
   *
   * @param users the stored users
   * @param encoder checks passwords against stored hashes
   * @param throttle the sign-in lockout
   * @param issuer mints the token
   */
  public AuthenticationService(
      UserRepository users, PasswordEncoder encoder, SignInThrottle throttle, TokenIssuer issuer) {
    this.users = users;
    this.encoder = encoder;
    this.throttle = throttle;
    this.issuer = issuer;
    this.unknownHash = encoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Attempts a sign-in.
   *
   * @param request the submitted login and password; either may be null
   * @param address where the attempt came from
   * @return the outcome; never an exception for a bad credential
   */
  public SignInOutcome signIn(SignInRequest request, String address) {
    final String login = normalise(request == null ? null : request.login());
    final String key = login + '@' + address;
    final Optional<Duration> locked = throttle.lockedFor(key);
    final SignInOutcome outcome;
    if (locked.isPresent()) {
      outcome = new SignInOutcome.Throttled(locked.get());
    } else {
      final Optional<UserDocument> user = authenticate(login, request);
      if (user.isPresent()) {
        throttle.recordSuccess(key);
        outcome = new SignInOutcome.Success(respond(user.get()));
      } else {
        throttle.recordFailure(key);
        outcome = new SignInOutcome.Rejected();
      }
    }
    return outcome;
  }

  /**
   * Issues a fresh token to a user who is already signed in, which is what keeps a session alive
   * while it is in use: each token lives for the idle timeout, so a session ends after that long
   * without a refresh. The user is read again, so the new token carries their current role and one
   * who was removed or disabled is not given another.
   *
   * @param staff the user the presented token is for
   * @return the new response
   * @throws UnauthorizedException if the user no longer exists or is disabled
   */
  public SignInResponse refresh(StaffPrincipal staff) {
    return users
        .findById(staff.id())
        .filter(UserDocument::enabled)
        .map(this::respond)
        .orElseThrow(UnauthorizedException::new);
  }

  private Optional<UserDocument> authenticate(String login, SignInRequest request) {
    final Optional<UserDocument> candidate =
        login.isEmpty() ? Optional.empty() : users.findByLogin(login);
    final String hash = candidate.map(UserDocument::passwordHash).orElse(unknownHash);
    final boolean matched = matches(request == null ? null : request.password(), hash);
    return candidate.filter(user -> matched && user.enabled());
  }

  private boolean matches(String password, String hash) {
    final boolean usable =
        password != null
            && !password.isBlank()
            && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    final boolean matched = encoder.matches(usable ? password : "unusable", hash);
    return usable && matched;
  }

  private SignInResponse respond(UserDocument user) {
    final TokenIssuer.IssuedToken token =
        issuer.issue(new StaffPrincipal(user.id(), user.login(), user.role()));
    return new SignInResponse(
        token.value(), "Bearer", token.lifetime().toSeconds(), user.login(), user.role());
  }

  private static String normalise(String login) {
    return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
  }
}
