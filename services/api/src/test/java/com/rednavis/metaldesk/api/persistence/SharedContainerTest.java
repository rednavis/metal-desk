package com.rednavis.metaldesk.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Proves the suite starts one MongoDB container, not one per test class: whichever test classes
 * have run by now, every one of them recorded the same container.
 */
class SharedContainerTest extends MongoTestSupport {

  @Test
  void everyTestClassSoFarUsedTheSameContainer() {
    assertEquals(Set.of(containerId()), observedContainerIds());
  }
}
