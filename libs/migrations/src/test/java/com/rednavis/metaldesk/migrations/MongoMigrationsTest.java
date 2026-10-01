package com.rednavis.metaldesk.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.rednavis.metaldesk.persistence.testing.SharedMongo;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** The migrations against a real MongoDB: what they create, and that running twice is harmless. */
class MongoMigrationsTest {

  private static final String LOGIN = "login";
  private static final String EMAIL = "email";
  private static final String HASH = "passwordHash";
  private static final String ADMIN = "admin";
  private static final String USERS = "users";

  private static String uri(String database) {
    return SharedMongo.connectionString().replace("/metaldesk", "/" + database);
  }

  private static String freshDatabase() {
    return "migrations_" + UUID.randomUUID().toString().replace("-", "");
  }

  @Test
  void firstMigrationCreatesTheTwoUsersWithHashedPasswords() {
    final String name = freshDatabase();

    MongoMigrations.run(uri(name));

    try (MongoClient client = MongoClients.create(uri(name))) {
      final MongoCollection<Document> users = client.getDatabase(name).getCollection(USERS);
      assertEquals(2, users.countDocuments());
      final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

      final Document admin = users.find(new Document(LOGIN, ADMIN)).first();
      assertEquals("admin@admin.by", admin.getString(EMAIL));
      assertEquals("ADMIN", admin.getString("role"));
      assertEquals(true, admin.getBoolean("enabled"));
      assertNotEquals(ADMIN, admin.getString(HASH));
      assertTrue(encoder.matches(ADMIN, admin.getString(HASH)));

      final Document manager = users.find(new Document(LOGIN, "manager")).first();
      assertEquals("manager@manager.by", manager.getString(EMAIL));
      assertEquals("MANAGER", manager.getString("role"));
      assertTrue(encoder.matches("manager", manager.getString(HASH)));
    }
  }

  @Test
  void runningAgainChangesNothing() {
    final String name = freshDatabase();

    MongoMigrations.run(uri(name));
    MongoMigrations.run(uri(name));

    try (MongoClient client = MongoClients.create(uri(name))) {
      assertEquals(2, client.getDatabase(name).getCollection(USERS).countDocuments());
    }
  }

  @Test
  void loginAndEmailAreUnique() {
    final String name = freshDatabase();
    MongoMigrations.run(uri(name));

    try (MongoClient client = MongoClients.create(uri(name))) {
      final MongoCollection<Document> users = client.getDatabase(name).getCollection(USERS);
      final Document sameLogin = new Document(LOGIN, ADMIN).append(EMAIL, "other@x.by");
      final Document sameEmail = new Document(LOGIN, "other").append(EMAIL, "admin@admin.by");
      assertThrows(MongoWriteException.class, () -> users.insertOne(sameLogin));
      assertThrows(MongoWriteException.class, () -> users.insertOne(sameEmail));
    }
  }

  @Test
  void connectionStringWithoutDatabaseIsRefused() {
    assertThrows(
        IllegalArgumentException.class, () -> MongoMigrations.run("mongodb://localhost:27017"));
  }
}
