package com.rednavis.metaldesk.api;

import com.rednavis.metaldesk.persistence.PersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Entry point for the {@code metal-api} customer-facing backend. */
@SpringBootApplication
@Import(PersistenceConfiguration.class)
public class ApiApplication {

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}
