package com.rednavis.metaldesk.api.persistence;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * The base of every test that needs a real MongoDB: one container for the whole test suite, and the
 * Spring context pointed at it.
 *
 * <p>The container is a JVM-wide singleton, started once in a static initializer and left to
 * Testcontainers' reaper to remove, instead of being started per test class. A container per class
 * would turn a two-minute suite into twenty and get blamed on MongoDB. Gradle runs a module's test
 * classes in one JVM, so all of them share it; {@link #observedContainerIds()} lets a test prove
 * it.
 *
 * <p>A plain (non-replica-set) server is enough: nothing here uses a transaction, and the sequence
 * allocator relies only on the atomicity of a single-document {@code findAndModify}.
 */
@Slf4j
@SpringBootTest
public class MongoTestSupport {

  private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");
  private static final Set<String> OBSERVED_IDS = ConcurrentHashMap.newKeySet();

  static {
    MONGO.start();
    final String id = MONGO.getContainerId();
    log.info("Started the suite's shared MongoDB container {}", id);
  }

  /** Points the application at the shared container. */
  @DynamicPropertySource
  public static void mongoProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.mongodb.uri", () -> MONGO.getConnectionString() + "/metaldesk");
    // Tests put the reference prices they need into the cache themselves.
    registry.add("metaldesk.market-data.refresh.enabled", () -> "false");
  }

  /** Creates the base; subclasses are the tests. */
  protected MongoTestSupport() {
    // Nothing to set up: the container is static, and Spring injects the rest.
  }

  /** Records which container the running test class talks to. */
  @BeforeAll
  public static void recordContainer() {
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
  public String containerId() {
    return MONGO.getContainerId();
  }
}
