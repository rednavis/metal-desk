package com.rednavis.metaldesk.migrations;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rednavis.metaldesk.migrations.changes.C001CreateUsers;
import com.rednavis.metaldesk.migrations.changes.C002SeedCategories;
import com.rednavis.metaldesk.migrations.changes.C003SeedProducts;
import com.rednavis.metaldesk.migrations.changes.C004SeedCarts;
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver;
import io.mongock.runner.standalone.MongockStandalone;
import io.mongock.runner.standalone.RunnerStandaloneBuilder;
import java.util.List;

/**
 * Applies every migration that has not been applied to the database yet.
 *
 * <p>Mongock records what it has applied in the database itself and holds a lock while it runs, so
 * two applications starting at once (services/api and apps/admin share one database) cannot apply a
 * migration twice: the second waits, then finds nothing left to do.
 *
 * <p>The change units are listed here, in order, rather than found by scanning the classpath, so
 * adding a migration is one visible line and nothing depends on classpath scanning working.
 */
public final class MongoMigrations {

  /** Every change unit, oldest first. */
  private static final List<Class<?>> CHANGE_UNITS =
      List.of(
          C001CreateUsers.class,
          C002SeedCategories.class,
          C003SeedProducts.class,
          C004SeedCarts.class);

  private MongoMigrations() {}

  /**
   * Applies the pending migrations.
   *
   * @param uri the MongoDB connection string, naming the database to migrate
   * @throws IllegalArgumentException if the connection string names no database
   */
  public static void run(String uri) {
    final String database = new ConnectionString(uri).getDatabase();
    if (database == null) {
      throw new IllegalArgumentException("The MongoDB connection string names no database");
    }
    try (MongoClient client = MongoClients.create(uri)) {
      final RunnerStandaloneBuilder builder =
          MongockStandalone.builder()
              .setDriver(MongoSync4Driver.withDefaultLock(client, database))
              // A transaction needs a replica set, which a single local server is not.
              .setTransactional(false);
      CHANGE_UNITS.forEach(builder::addMigrationClass);
      builder.buildRunner().execute();
    }
  }
}
