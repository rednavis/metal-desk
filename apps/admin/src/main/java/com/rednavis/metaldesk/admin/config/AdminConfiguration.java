package com.rednavis.metaldesk.admin.config;

import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The beans every part of the staff API shares. The mail sender is the in-process fake, the only
 * one there is (ADR-0002); a real sender goes behind the same {@code MailSender} interface.
 */
@Configuration(proxyBeanMethods = false)
public class AdminConfiguration {

  /**
   * The wall clock, a bean so that tests can replace it.
   *
   * @return the system UTC clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Renders the transactional mail templates.
   *
   * @return the renderer
   */
  @Bean
  public MailRenderer mailRenderer() {
    return new MailRenderer();
  }

  /**
   * Sends mail in-process.
   *
   * @return the fake sender
   */
  @Bean
  public InProcessMailSender mailSender() {
    return new InProcessMailSender();
  }
}
