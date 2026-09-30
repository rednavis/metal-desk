package com.rednavis.metaldesk.api.persistence.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A verification challenge as stored (BRD FR-2.6).
 *
 * <p>It holds a <em>hash</em> of the code, never the code: a verification or reset code in the
 * database would be a credential. The {@code expiresAt} index expires the document when it is due,
 * which is housekeeping only; expiry is also checked on every confirmation, because the database's
 * expiry sweep runs about once a minute.
 *
 * @param reference the opaque id the client quotes when confirming; not a secret on its own
 * @param purpose the purpose name, so a code for one purpose cannot complete another
 * @param subject who the challenge is bound to (a customer id), or null until it is bound
 * @param email the address the code was sent to
 * @param codeHash the encoded hash of the code
 * @param expiresAt when the challenge stops being usable
 * @param attempts how many confirmation attempts have been made
 * @param status {@code PENDING}, {@code CONFIRMED} or {@code INVALIDATED}
 */
@Document("verification_challenges")
public record VerificationChallengeDocument(
    @Id String reference,
    String purpose,
    @Indexed(sparse = true) String subject,
    String email,
    String codeHash,
    @Indexed(expireAfter = "0s") Instant expiresAt,
    int attempts,
    String status) {

  /** Redacts the hash, so a logged document does not leak it. */
  @Override
  public String toString() {
    return "VerificationChallengeDocument[purpose=" + purpose + ", status=" + status + ']';
  }
}
