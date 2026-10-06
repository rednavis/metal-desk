package com.rednavis.metaldesk.api.persistence;

import com.rednavis.metaldesk.persistence.testing.SharedMongo;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The base of every test in this module that needs a real MongoDB: the suite's one shared container
 * (see {@link SharedMongo}) and the Spring context pointed at it.
 */
@SpringBootTest
public class MongoTestSupport {

  /** Points the application at the shared container. */
  @DynamicPropertySource
  public static void mongoProperties(DynamicPropertyRegistry registry) {
    SharedMongo.registerProperties(registry);
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
    SharedMongo.recordUse();
  }

  /**
   * The ids of every container the test classes run so far have used.
   *
   * @return the ids; more than one entry means the suite is starting a container per class
   */
  public static Set<String> observedContainerIds() {
    return SharedMongo.observedContainerIds();
  }

  /**
   * The id of the shared container.
   *
   * @return the container id
   */
  public String containerId() {
    return SharedMongo.containerId();
  }
}
