package com.rednavis.metaldesk.api.account.verification;

import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.RenderedMail;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Renders and sends the mail that carries a verification code, through {@code libs/mail}.
 *
 * <p>The model holds the recipient's name, the code, how long it lives, and a link made of the
 * configured base plus the reference and code. Each template uses the placeholders it needs.
 *
 * <p><strong>The plaintext email-verification code of a registration is deliberately written to the
 * log</strong> (at INFO, with the recipient), on the project owner's explicit instruction, so it
 * can be read from the log while mail is only the in-process fake. A password-reset code is never
 * logged. A deployment with a real mail sender must stop this: the log would then hold a working
 * credential.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationMailer {

  private final MailRenderer renderer;
  private final MailSender sender;
  private final VerificationProperties properties;

  /**
   * Sends a code.
   *
   * @param purpose what the code is for, which decides the template and how long it is valid
   * @param email the recipient
   * @param name how to address the recipient
   * @param locale the language of the mail
   * @param reference the challenge reference
   * @param code the plaintext code
   * @return a signal that completes when the mail has been handed to the sender
   */
  public Mono<Void> send(
      VerificationPurpose purpose,
      EmailAddress email,
      String name,
      Locale locale,
      String reference,
      String code) {
    final Map<String, Object> model =
        Map.of(
            "name",
            name,
            "code",
            code,
            "link",
            properties.linkBase() + "?reference=" + reference + "&code=" + code,
            "validity",
            properties.ttlFor(purpose).toMinutes() + " minutes");
    if (purpose != VerificationPurpose.PASSWORD_RESET && log.isInfoEnabled()) {
      log.info(
          "Email verification code for {} ({}): {} (reference {})",
          email.value(),
          purpose,
          code,
          reference);
    }
    final RenderedMail rendered = renderer.render(purpose.template(), model, locale);
    return sender.send(
        TransactionalMail.from(purpose.template(), rendered, List.of(email), List.of()));
  }
}
