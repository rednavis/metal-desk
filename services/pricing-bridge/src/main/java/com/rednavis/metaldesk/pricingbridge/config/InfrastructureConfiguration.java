package com.rednavis.metaldesk.pricingbridge.config;

import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two sources of non-determinism the service needs, as beans so that tests can replace them.
 */
@Configuration(proxyBeanMethods = false)
public class InfrastructureConfiguration {

  /**
   * The wall clock: observation times, tick arrival and feed staleness are all read from it.
   *
   * @return the system UTC clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * The source of reconnect jitter. It is deliberately not seeded: jitter exists to keep instances
   * from reconnecting in step, which a fixed seed would defeat. Tests inject their own.
   *
   * @return the platform's default generator
   */
  @Bean
  public RandomGenerator jitterRandom() {
    return RandomGenerator.getDefault();
  }
}
