package com.rednavis.metaldesk.api.mail;

import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@code libs/mail} abstraction for {@code services/api}.
 *
 * <p>The only sender is the in-process fake (ADR-0002): mail is recorded in memory, and tests read
 * it back from there. A real sender is added behind {@code MailSender} when a deployment needs one;
 * nothing that sends mail changes when it is.
 */
@Configuration
public class MailConfiguration {

  /**
   * The template renderer.
   *
   * @return the renderer
   */
  @Bean
  public MailRenderer mailRenderer() {
    return new MailRenderer();
  }

  /**
   * The mail sender, typed as the fake so tests can inspect what was sent; it is injected as a
   * {@code MailSender} everywhere else.
   *
   * @return the in-process sender
   */
  @Bean
  public InProcessMailSender mailSender() {
    return new InProcessMailSender();
  }
}
