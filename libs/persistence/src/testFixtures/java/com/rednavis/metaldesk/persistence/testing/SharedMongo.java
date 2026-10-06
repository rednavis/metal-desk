package com.rednavis.metaldesk.persistence.testing;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * One MongoDB container for a whole test JVM, and the wiring that points a Spring context at it.
 *
 * <p>The container is a JVM-wide singleton, started once in a static initializer and left to
 * Testcontainers' reaper to remove, instead of being started per test class. A container per class
 * would turn a two-minute suite into twenty and get blamed on MongoDB. Gradle runs a module's test
 * classes in one JVM, so all of them share it; {@link #observedContainerIds()} lets a test prove
 * it.
 *
 * <p>A plain (non-replica-set) server is enough: nothing here uses a transaction.
 */
public final class SharedMongo {

  private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");
  private static final Set<String> OBSERVED_IDS = ConcurrentHashMap.newKeySet();

  static {
    MONGO.start();
  }

  private SharedMongo() {}

  /**
   * Points the application's Mongo connection at the shared container.
   *
   * @param registry the registry a {@code @DynamicPropertySource} method receives
   */
  public static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.mongodb.uri", SharedMongo::connectionString);
  }

  /**
   * The connection string of the shared container, including the database.
   *
   * @return the connection string
   */
  public static String connectionString() {
    return MONGO.getConnectionString() + "/metaldesk";
  }

  /** Records that the running test class talks to the shared container. */
  public static void recordUse() {
    OBSERVED_IDS.add(MONGO.getContainerId());
  }

  /**
   * The ids of every container the test classes run so far have used.
   *
   * @return the ids; more than one entry means the suite is starting a container per class
   */
  public static Set<String> observedContainerIds() {
    return Set.copyOf(OBSERVED_IDS);
  }

  /**
   * The id of the shared container.
   *
   * @return the container id
   */
  public static String containerId() {
    return MONGO.getContainerId();
  }
}
