package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.cart.CartId;
import com.rednavis.metaldesk.api.persistence.document.CheckoutSessionDocument;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Persists checkout sessions, and makes every change a compare-and-set on the version, so two
 * simultaneous edits cannot silently overwrite each other. Every change also pushes the expiry out.
 */
@Component
@RequiredArgsConstructor
public class CheckoutSessionStore {

  private static final int ATTEMPTS = 5;
  private static final int REFERENCE_BYTES = 16;

  private final ReactiveMongoTemplate mongo;
  private final CheckoutSessionMapper mapper;
  private final CheckoutProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /**
   * Starts a session.
   *
   * @param owner the signed-in customer, or empty for a guest
   * @param source where the basket came from
   * @param cart the cart it came from, if any
   * @param lines the basket lines as quoted now
   * @return the new session
   */
  public Mono<CheckoutSession> create(
      Optional<CustomerId> owner,
      CheckoutSession.Source source,
      Optional<CartId> cart,
      List<OrderLine> lines) {
    final Instant now = clock.instant();
    final CheckoutSession session =
        new CheckoutSession(
            newReference(),
            owner,
            source,
            cart,
            lines,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            now,
            now,
            now.plus(properties.sessionTtl()));
    return mongo.insert(mapper.toDocument(session)).map(mapper::toDomain);
  }

  /**
   * Finds a session that has not expired.
   *
   * @param id the session's reference
   * @return the session, or empty
   */
  public Mono<CheckoutSession> find(String id) {
    return mongo
        .findById(id, CheckoutSessionDocument.class)
        .filter(document -> document.expiresAt().isAfter(clock.instant()))
        .map(mapper::toDomain);
  }

  /**
   * Applies a change to a session.
   *
   * @param id the session's reference
   * @param change a pure function from the session as read to the session wanted
   * @return the session after the change; empty if there is no such session
   * @throws ConflictException with code {@code checkout.busy} if it kept changing under this
   *     request
   */
  public Mono<CheckoutSession> update(String id, UnaryOperator<CheckoutSession> change) {
    return Mono.defer(() -> attempt(id, change))
        .retryWhen(
            Retry.max(ATTEMPTS)
                .filter(OptimisticLockingFailureException.class::isInstance)
                .onRetryExhaustedThrow(
                    (spec, signal) ->
                        new ConflictException(
                            "checkout.busy", "The checkout is being changed, try again")));
  }

  private Mono<CheckoutSession> attempt(String id, UnaryOperator<CheckoutSession> change) {
    return find(id).flatMap(read -> write(read, change.apply(read)));
  }

  private Mono<CheckoutSession> write(CheckoutSession read, CheckoutSession wanted) {
    final Instant now = clock.instant();
    final CheckoutSession next =
        new CheckoutSession(
            wanted.id(),
            wanted.owner(),
            wanted.source(),
            wanted.cart(),
            wanted.lines(),
            wanted.details(),
            wanted.consent(),
            wanted.conversion(),
            read.version() + 1,
            wanted.createdAt(),
            now,
            now.plus(properties.sessionTtl()));
    final Query query =
        Query.query(Criteria.where("_id").is(read.id()).and("version").is(read.version()));
    return mongo
        .replace(query, mapper.toDocument(next))
        .flatMap(
            result ->
                result.getMatchedCount() == 1
                    ? Mono.just(next)
                    : Mono.error(new OptimisticLockingFailureException("stale session")));
  }

  private String newReference() {
    final byte[] bytes = new byte[REFERENCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
