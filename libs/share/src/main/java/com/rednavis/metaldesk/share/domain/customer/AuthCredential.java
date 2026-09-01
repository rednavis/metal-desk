package com.rednavis.metaldesk.share.domain.customer;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * How one principal signs in (BRD FR-2.1): a sign-in identifier, a reference to the stored password
 * hash, and whether the credential may currently be used.
 *
 * <p>It is a separate type from {@link Customer} because FR-2.5 lets one authenticated principal
 * reach several accounts, so credential and customer are not one-to-one. Which customers a
 * credential can reach is deliberately not modelled here; that is account-switching, and is
 * persisted by the auth service.
 *
 * <p><strong>There is no {@code verify(plaintext)} here, and there must not be.</strong> Checking a
 * password needs a password encoder, which is a Spring Security concern, and {@code libs/share}
 * depends on no Spring artifact — adding one would drag Spring Security into every module. The
 * encoder, hash issuance and throttling live in {@code services/api} (task T-032), which compares
 * the submitted password against {@link #passwordHash()}. The type never holds or accepts a
 * plaintext password; the only string it takes is the already-computed hash.
 *
 * <p>{@link #toString()} redacts the hash so a credential that is logged does not leak it.
 *
 * @param identifier the sign-in identifier, never null
 * @param passwordHash the opaque encoded hash produced by the password encoder, never blank
 * @param state whether the credential may sign in, never null
 */
public record AuthCredential(AuthIdentifier identifier, String passwordHash, State state) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the identifier or state is null, or the hash is null or blank
   */
  public AuthCredential {
    if (identifier == null) {
      throw new ValidationException(
          "auth-credential.identifier-missing", "Credential identifier must not be null");
    }
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new ValidationException(
          "auth-credential.hash-blank", "Credential password hash must not be null or blank");
    }
    if (state == null) {
      throw new ValidationException(
          "auth-credential.state-missing", "Credential state must not be null");
    }
  }

  /**
   * Returns a description that omits the password hash.
   *
   * @return the identifier and state, with the hash redacted
   */
  @Override
  public String toString() {
    return "AuthCredential[identifier=" + identifier + ", passwordHash=***, state=" + state + ']';
  }

  /** Whether a credential may currently be used to sign in. */
  public enum State {

    /** The credential works. */
    ACTIVE,

    /** Staff disabled the credential; sign-in is refused, as for a wrong password (FR-2.2). */
    DISABLED;

    /**
     * Tells whether sign-in with this credential may proceed to the password check.
     *
     * @return {@code true} only for {@link #ACTIVE}
     */
    public boolean canSignIn() {
      return this == ACTIVE;
    }
  }
}
