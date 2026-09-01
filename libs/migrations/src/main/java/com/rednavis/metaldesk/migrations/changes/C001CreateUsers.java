package com.rednavis.metaldesk.migrations.changes;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Creates the {@code users} collection, the people who sign in to the back office, with a unique
 * index on both login and email, and adds the two initial users.
 *
 * <p><strong>The two users are well-known development accounts</strong> ({@code admin/admin} and
 * {@code manager/manager}), created in every environment this runs in. They exist so a fresh
 * database can be signed in to at once; a real deployment must change or remove them before it is
 * reachable by anyone else. Passwords are stored only as BCrypt hashes, computed here.
 *
 * <p>The field names and values are written out as literals instead of going through the
 * persistence module's classes on purpose: a migration describes the database as it was when it was
 * written, and must not change meaning when those classes do.
 */
@ChangeUnit(id = "create-users", order = "001", author = "metaldesk")
public class C001CreateUsers {

  private static final String USERS = "users";

  /** Creates the change unit; Mongock instantiates it. */
  public C001CreateUsers() {
    // Nothing to set up: Mongock injects what the methods ask for.
  }

  /**
   * Creates the indexes and inserts the initial users.
   *
   * @param database the database being migrated
   */
  @Execution
  public void execute(MongoDatabase database) {
    final MongoCollection<Document> users = database.getCollection(USERS);
    users.createIndex(Indexes.ascending("login"), new IndexOptions().unique(true));
    users.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    final Instant now = Instant.now();
    users.insertMany(
        List.of(
            user(encoder, "admin", "admin@admin.by", "admin", "ADMIN", now),
            user(encoder, "manager", "manager@manager.by", "manager", "MANAGER", now)));
  }

  /**
   * Removes everything {@link #execute} created.
   *
   * @param database the database being migrated
   */
  @RollbackExecution
  public void rollback(MongoDatabase database) {
    database.getCollection(USERS).drop();
  }

  private static Document user(
      BCryptPasswordEncoder encoder,
      String login,
      String email,
      String password,
      String role,
      Instant createdAt) {
    return new Document(
        Map.of(
            "_id", UUID.randomUUID().toString(),
            "login", login,
            "email", email,
            "passwordHash", encoder.encode(password),
            "role", role,
            "enabled", true,
            "createdAt", createdAt));
  }
}
