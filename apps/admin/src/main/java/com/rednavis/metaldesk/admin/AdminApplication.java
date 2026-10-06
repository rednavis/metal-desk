package com.rednavis.metaldesk.admin;

import com.rednavis.metaldesk.migrations.MigrationsConfiguration;
import com.rednavis.metaldesk.persistence.PersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * Entry point for the {@code metal-admin} staff API: a pure JSON API, no server-rendered UI. It is
 * the one MVC (blocking, virtual-thread) module of the platform (ADR-0004); its only client is
 * {@code apps/admin-web}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({PersistenceConfiguration.class, MigrationsConfiguration.class})
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
