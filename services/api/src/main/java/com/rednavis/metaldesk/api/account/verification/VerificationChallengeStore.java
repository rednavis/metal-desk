package com.rednavis.metaldesk.api.account.verification;

import com.rednavis.metaldesk.api.persistence.repository.VerificationChallengeRepository;
import com.rednavis.metaldesk.persistence.document.VerificationChallengeDocument;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Persists verification challenges and makes each state change one atomic database operation.
 *
 * <p>That is what enforces single use and the attempt limit under concurrency: counting an attempt
 * and marking a challenge confirmed are conditional updates ({@code findAndModify} with the state
 * in the filter), never a read followed by a write, so two simultaneous confirmations with the
 * right code cannot both succeed and a burst of guesses cannot exceed the limit.
 */
@Component
@RequiredArgsConstructor
public class VerificationChallengeStore {

  private static final String STATUS = "status";
  private static final FindAndModifyOptions RETURN_NEW =
      FindAndModifyOptions.options().returnNew(true);

  private final VerificationChallengeRepository repository;
  private final ReactiveMongoTemplate mongo;

  /**
   * Stores a new challenge.
   *
   * @param challenge the challenge
   * @return a signal that completes when it is stored
   */
  public Mono<Void> save(VerificationChallenge challenge) {
    return repository.save(toDocument(challenge)).then();
  }

  /**
   * Looks a challenge up in any state.
   *
   * @param reference the challenge reference
   * @return the challenge, or empty
   */
  public Mono<VerificationChallenge> find(String reference) {
    return repository.findById(reference).map(VerificationChallengeStore::toChallenge);
  }

  /**
   * Counts one confirmation attempt, if the challenge can still take one.
   *
   * @param reference the challenge reference
   * @param now the current time
   * @param maxAttempts the attempt limit
   * @return the challenge with its attempt counted, or empty if it is unknown, not pending,
   *     expired, or already out of attempts
   */
  public Mono<VerificationChallenge> countAttempt(String reference, Instant now, int maxAttempts) {
    final Query query =
        Query.query(
            Criteria.where("_id")
                .is(reference)
                .and(STATUS)
                .is(VerificationChallenge.Status.PENDING.name())
                .and("expiresAt")
                .gt(now)
                .and("attempts")
                .lt(maxAttempts));
    return mongo
        .findAndModify(
            query, new Update().inc("attempts", 1), RETURN_NEW, VerificationChallengeDocument.class)
        .map(VerificationChallengeStore::toChallenge);
  }

  /**
   * Marks a pending challenge confirmed, once.
   *
   * @param reference the challenge reference
   * @return the confirmed challenge, or empty if it was no longer pending
   */
  public Mono<VerificationChallenge> confirm(String reference) {
    final Query query =
        Query.query(
            Criteria.where("_id")
                .is(reference)
                .and(STATUS)
                .is(VerificationChallenge.Status.PENDING.name()));
    return mongo
        .findAndModify(
            query,
            new Update().set(STATUS, VerificationChallenge.Status.CONFIRMED.name()),
            RETURN_NEW,
            VerificationChallengeDocument.class)
        .map(VerificationChallengeStore::toChallenge);
  }

  /**
   * Binds a pending, unbound challenge to a subject.
   *
   * @param reference the challenge reference
   * @param subject the customer id to bind to
   * @return whether the challenge was bound
   */
  public Mono<Boolean> bind(String reference, String subject) {
    final Query query =
        Query.query(
            Criteria.where("_id")
                .is(reference)
                .and(STATUS)
                .is(VerificationChallenge.Status.PENDING.name())
                .and("subject")
                .is(null));
    return mongo
        .updateFirst(
            query, new Update().set("subject", subject), VerificationChallengeDocument.class)
        .map(result -> result.getModifiedCount() == 1);
  }

  /**
   * Cancels every pending challenge of a purpose bound to a subject.
   *
   * @param purpose the purpose
   * @param subject the customer id
   * @return a signal that completes when they are cancelled
   */
  public Mono<Void> invalidatePending(VerificationPurpose purpose, String subject) {
    final Query query =
        Query.query(
            Criteria.where("purpose")
                .is(purpose.name())
                .and("subject")
                .is(subject)
                .and(STATUS)
                .is(VerificationChallenge.Status.PENDING.name()));
    return mongo
        .updateMulti(
            query,
            new Update().set(STATUS, VerificationChallenge.Status.INVALIDATED.name()),
            VerificationChallengeDocument.class)
        .then();
  }

  private static VerificationChallengeDocument toDocument(VerificationChallenge challenge) {
    return new VerificationChallengeDocument(
        challenge.reference(),
        challenge.purpose().name(),
        challenge.subject(),
        challenge.email(),
        challenge.codeHash(),
        challenge.expiresAt(),
        challenge.attempts(),
        challenge.status().name());
  }

  private static VerificationChallenge toChallenge(VerificationChallengeDocument document) {
    return new VerificationChallenge(
        document.reference(),
        VerificationPurpose.valueOf(document.purpose()),
        document.subject(),
        document.email(),
        document.codeHash(),
        document.expiresAt(),
        document.attempts(),
        VerificationChallenge.Status.valueOf(document.status()));
  }
}
