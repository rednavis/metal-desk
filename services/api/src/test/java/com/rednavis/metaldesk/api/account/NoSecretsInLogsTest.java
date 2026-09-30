package com.rednavis.metaldesk.api.account;

import static org.junit.jupiter.api.Assertions.assertFalse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rednavis.metaldesk.mail.TransactionalMail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * No verification code, reset code or reset link appears in any log output (BRD FR-2.2's spirit).
 */
class NoSecretsInLogsTest extends AccountTestSupport {

  private static final String REFERENCE = "reference";

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger root;
  private Level previous;

  @BeforeEach
  void capture() {
    root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    previous = root.getLevel();
    root.setLevel(Level.TRACE);
    appender.start();
    root.addAppender(appender);
  }

  @AfterEach
  void release() {
    root.detachAppender(appender);
    root.setLevel(previous);
    appender.stop();
  }

  private List<String> logged() {
    return appender.list.stream()
        .map(event -> event.getFormattedMessage() + " " + event.getThrowableProxy())
        // The test's own WebTestClient logs the request bodies it builds from plain maps; that is
        // the test talking, not the service. The service's request types redact their secrets.
        .filter(line -> !line.contains("Encoding [{"))
        .toList();
  }

  private void assertNeverLogged(String... secrets) {
    for (final String secret : secrets) {
      assertFalse(
          logged().stream().anyMatch(line -> line.contains(secret)),
          "a secret appeared in the logs: "
              + logged().stream().filter(line -> line.contains(secret)).findFirst().orElse(""));
    }
  }

  @Test
  void registrationConfirmationAndResetLogNoCodes() {
    final String email = freshEmail();
    final Object reference = register(email).body().get(REFERENCE);
    final String typed = MailInspector.typedCode(mailTo(email).get(0));

    // A wrong code and a right one, so both the failure and the success paths are logged.
    client
        .post()
        .uri("/api/account/verify-email")
        .bodyValue(Map.of(REFERENCE, reference, "code", "000000"))
        .exchange();
    client
        .post()
        .uri("/api/account/verify-email")
        .bodyValue(Map.of(REFERENCE, reference, "code", typed))
        .exchange()
        .expectStatus()
        .isOk();
    client
        .post()
        .uri("/api/account/password-reset/request")
        .bodyValue(Map.of("email", email))
        .exchange();
    final TransactionalMail link = mailTo(email).get(1);
    final String resetCode = MailInspector.linkCode(link);
    client
        .post()
        .uri("/api/account/password-reset/confirm")
        .bodyValue(
            Map.of(
                REFERENCE,
                MailInspector.linkReference(link),
                "code",
                resetCode,
                "newPassword",
                "a-brand-new-password"))
        .exchange()
        .expectStatus()
        .isOk();

    assertFalse(logged().isEmpty(), "the test captured no log output at all");
    assertNeverLogged(typed, resetCode, "a-brand-new-password", PASSWORD, "code=" + resetCode);
  }
}
