package com.rednavis.metaldesk.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the {@code metal-api} customer-facing backend. */
@SpringBootApplication
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
