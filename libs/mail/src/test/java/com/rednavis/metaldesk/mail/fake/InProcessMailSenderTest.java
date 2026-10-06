package com.rednavis.metaldesk.mail.fake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.mail.MailFixtures;
import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class InProcessMailSenderTest {

  private final InProcessMailSender sender = new InProcessMailSender();

  private void send(TransactionalMail mail) {
    StepVerifier.create(sender.send(mail)).verifyComplete();
  }

  @Test
  void recordsSendAndTheQueriesFindIt() {
    send(MailFixtures.mail(MailTemplate.EMAIL_VERIFICATION));
    send(
        new TransactionalMail(
            MailTemplate.ORDER_NOTIFICATION_STAFF,
            List.of(MailFixtures.STAFF),
            "New order",
            "Body",
            Locale.ENGLISH,
            List.of()));
    assertEquals(2, sender.sent().size());
    assertEquals(1, sender.sentTo(MailFixtures.CUSTOMER).size());
    assertEquals(1, sender.sentTo(MailFixtures.STAFF).size());
    assertEquals(
        MailTemplate.EMAIL_VERIFICATION,
        sender.sentTo(MailFixtures.CUSTOMER).get(0).mail().template());
    assertEquals(1, sender.sentOf(MailTemplate.ORDER_NOTIFICATION_STAFF).size());
    assertTrue(sender.sentOf(MailTemplate.PASSWORD_RESET).isEmpty());
  }

  @Test
  void clearEmptiesTheRecordButSequenceNumbersKeepCounting() {
    send(MailFixtures.mail(MailTemplate.EMAIL_VERIFICATION));
    sender.clear();
    assertTrue(sender.sent().isEmpty());
    send(MailFixtures.mail(MailTemplate.EMAIL_VERIFICATION));
    assertEquals(2, sender.sent().get(0).sequence());
  }

  @Test
  void sendIsRecordedWhenSubscribedNotWhenCalled() {
    final Mono<Void> pending = sender.send(MailFixtures.mail(MailTemplate.PASSWORD_RESET));
    assertTrue(sender.sent().isEmpty());
    StepVerifier.create(pending).verifyComplete();
    assertEquals(1, sender.sent().size());
  }

  @Test
  void concurrentSendsFromManyThreadsAllAppearOnceWithUniqueSequences() {
    final int count = 400;
    StepVerifier.create(
            Flux.range(0, count)
                .flatMap(
                    index ->
                        sender
                            .send(MailFixtures.mail(MailTemplate.ORDER_CONFIRMATION_CUSTOMER))
                            .subscribeOn(Schedulers.parallel()))
                .then())
        .verifyComplete();
    final Set<Long> sequences =
        sender.sent().stream().map(RecordedMail::sequence).collect(Collectors.toSet());
    assertEquals(count, sender.sent().size());
    assertEquals(count, sequences.size());
    assertEquals(1L, sequences.stream().min(Long::compare).orElseThrow());
    assertEquals(count, sequences.stream().max(Long::compare).orElseThrow());
  }

  @Test
  void recordedOrderFollowsTheSequence() {
    for (int index = 0; index < 5; index++) {
      send(MailFixtures.mail(MailTemplate.EMAIL_VERIFICATION));
    }
    final List<Long> order = sender.sent().stream().map(RecordedMail::sequence).toList();
    assertEquals(List.of(1L, 2L, 3L, 4L, 5L), order);
  }

  @Test
  void mailToSeveralRecipientsIsFoundByEachOfThem() {
    final EmailAddress second = new EmailAddress("second@example.com");
    send(
        new TransactionalMail(
            MailTemplate.INVOICE_CUSTOMER,
            List.of(MailFixtures.CUSTOMER, second),
            "Invoice",
            "Body",
            Locale.ENGLISH,
            List.of()));
    assertEquals(1, sender.sentTo(second).size());
    assertEquals(1, sender.sentTo(MailFixtures.CUSTOMER).size());
  }

  @Test
  void nullMailIsRefused() {
    StepVerifier.create(sender.send(null))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof ValidationException);
              assertEquals("mail-sender.mail-missing", ((ValidationException) error).code());
            })
        .verify();
    assertThrows(
        ValidationException.class,
        () -> new RecordedMail(0, MailFixtures.mail(MailTemplate.PASSWORD_RESET)));
  }

  @Test
  void sendReturnsReactiveTypeAndDoesNotBlock() throws NoSuchMethodException {
    assertEquals(
        Mono.class, MailSender.class.getMethod("send", TransactionalMail.class).getReturnType());
  }
}
