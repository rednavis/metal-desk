package com.rednavis.metaldesk.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the {@code metal-admin} back-office API. */
@SpringBootApplication
public class AdminApplication {

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(AdminApplication.class, args);
  }
}
