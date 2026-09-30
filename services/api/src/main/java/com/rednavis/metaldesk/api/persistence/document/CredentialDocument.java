package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.customer.AuthCredential;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * How a customer signs in, as stored: the password hash and whether it may be used.
 *
 * <p>It is keyed by the customer id, and the sign-in identifier (email or phone) is found on the
 * customer, so a customer with both has one password to change, not two. Which other customers the
 * credential may reach (BRD FR-2.5, account switching) is not stored yet.
 *
 * <p>The hash is an opaque encoded value from the password encoder; nothing here ever holds a
 * plaintext password.
 *
 * @param id the customer id
 * @param passwordHash the encoded password hash
 * @param state whether the credential may sign in
 */
@Document("credentials")
public record CredentialDocument(@Id String id, String passwordHash, AuthCredential.State state) {

  /** Redacts the hash, so a logged document does not leak it. */
  @Override
  public String toString() {
    return "CredentialDocument[id=" + id + ", passwordHash=***, state=" + state + ']';
  }
}
