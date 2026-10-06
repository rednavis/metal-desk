package com.rednavis.metaldesk.pricingbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the {@code metal-pricing-bridge} market-data service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PricingBridgeApplication {

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(PricingBridgeApplication.class, args);
  }
}
