package com.rednavis.metaldesk.persistence.document;

import com.rednavis.metaldesk.share.domain.user.UserRole;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A back-office user as stored: someone who may sign in to {@code apps/admin-web}.
 *
 * <p>Users are not customers and live in their own collection, so a customer's credential can never
 * sign in to the back office nor a user's to the storefront. Login and email are both unique, and
 * both are stored lower-cased. The collection and its unique indexes belong to the migration that
 * creates them, which is why there is no {@code @Indexed} here: a service that creates indexes from
 * annotations would race the migration and collide with it on the index name.
 *
 * <p>The hash is an opaque encoded value from the password encoder; nothing here ever holds a
 * plaintext password.
 *
 * @param id the user id
 * @param login the normalised (lower-case) login name, unique
 * @param email the normalised email address, unique
 * @param passwordHash the encoded password hash
 * @param role what the user may do
 * @param enabled whether the user may sign in
 * @param createdAt when the user was created
 */
@Document("users")
public record UserDocument(
    @Id String id,
    String login,
    String email,
    String passwordHash,
    UserRole role,
    boolean enabled,
    Instant createdAt) {

  /** Redacts the hash, so a logged document does not leak it. */
  @Override
  public String toString() {
    return "UserDocument[id="
        + id
        + ", login="
        + login
        + ", email="
        + email
        + ", passwordHash=***, role="
        + role
        + ", enabled="
        + enabled
        + ", createdAt="
        + createdAt
        + ']';
  }
}
