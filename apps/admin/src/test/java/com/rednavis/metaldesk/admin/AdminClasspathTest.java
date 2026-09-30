package com.rednavis.metaldesk.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * The module stays what ADR-0004 made it: MVC on virtual threads, with no WebFlux and no reactive
 * MongoDB driver anywhere on its runtime classpath.
 */
@SpringBootTest
class AdminClasspathTest {

  @Autowired private Environment environment;

  @Test
  void hasNoWebfluxAndNoReactiveMongoDriver() {
    final boolean offending =
        Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
            .anyMatch(
                entry ->
                    entry.contains("webflux") || entry.contains("mongodb-driver-reactivestreams"));
    assertFalse(offending, "WebFlux or the reactive MongoDB driver is on the classpath");
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("org.springframework.web.reactive.DispatcherHandler"));
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.mongodb.reactivestreams.client.MongoClient"));
  }

  @Test
  void runsOnVirtualThreads() {
    assertEquals("true", environment.getProperty("spring.threads.virtual.enabled"));
  }
}
