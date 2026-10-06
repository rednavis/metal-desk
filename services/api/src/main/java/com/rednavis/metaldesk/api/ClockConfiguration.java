package com.rednavis.metaldesk.api;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the one {@link Clock} the service reads time from, so tests can replace it. */
@Configuration
public class ClockConfiguration {

  /**
   * The system clock in UTC.
   *
   * @return the clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
