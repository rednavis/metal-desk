package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.persistence.document.CartDocument;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Persists carts, and makes every change a compare-and-set on the cart's version, so two
 * simultaneous changes cannot silently overwrite each other.
 *
 * <p>{@link #update} reads the cart, applies a pure function to it, and writes the result only if
 * the version is still the one it read; if not it starts again, a few times, and then gives up with
 * a {@link ConflictException}. Taking an anonymous cart away ({@link #claimAnonymous}) is one
 * atomic delete-and-return, so of two requests merging the same cart only one gets its lines.
 */
@Component
@RequiredArgsConstructor
public class CartStore {

  private static final int ATTEMPTS = 5;
  private static final int REFERENCE_BYTES = 16;
  private static final String ID = "_id";
  private static final String OWNER = "ownerId";

  private final ReactiveMongoTemplate mongo;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /**
   * Finds a cart by reference.
   *
   * @param id the reference
   * @return the cart, or empty
   */
  public Mono<Cart> find(String id) {
    return mongo.findById(id, CartDocument.class).map(CartMapping::toCart);
  }

  /**
   * Finds the cart a customer owns.
   *
   * @param customerId the customer's id
   * @return the cart, or empty
   */
  public Mono<Cart> findByOwner(String customerId) {
    return mongo
        .findOne(Query.query(Criteria.where(OWNER).is(customerId)), CartDocument.class)
        .map(CartMapping::toCart);
  }

  /**
   * Creates an empty cart with a fresh reference.
   *
   * @param owner the customer who owns it, or null for an anonymous cart
   * @return the new cart; an error signal if the customer already owns one
   */
  public Mono<Cart> create(String owner) {
    final Instant now = clock.instant();
    final CartDocument document = new CartDocument(newReference(), owner, List.of(), 0, now, now);
    return mongo.insert(document).map(CartMapping::toCart);
  }

  /**
   * Applies a change to a cart.
   *
   * @param id the cart's reference
   * @param change a pure function from the cart as read to the cart wanted; if it returns the same
   *     cart nothing is written
   * @return the cart after the change; empty if there is no such cart
   * @throws ConflictException with code {@code cart.busy} if it kept changing under this request
   */
  public Mono<Cart> update(String id, UnaryOperator<Cart> change) {
    return Mono.defer(() -> attempt(id, change))
        .retryWhen(
            Retry.max(ATTEMPTS)
                .filter(OptimisticLockingFailureException.class::isInstance)
                .onRetryExhaustedThrow(
                    (spec, signal) ->
                        new ConflictException(
                            "cart.busy", "The cart is being changed, try again")));
  }

  /**
   * Takes an anonymous cart out of circulation in one atomic step, returning what it held.
   *
   * @param id the anonymous cart's reference
   * @return the cart, or empty if it is gone or has an owner
   */
  public Mono<Cart> claimAnonymous(String id) {
    return mongo
        .findAndRemove(
            Query.query(Criteria.where(ID).is(id).and(OWNER).exists(false)), CartDocument.class)
        .map(CartMapping::toCart);
  }

  /**
   * Gives an anonymous cart to a customer.
   *
   * @param id the anonymous cart's reference
   * @param customerId the customer's id
   * @return whether it was adopted; false if it was gone or already had an owner
   */
  public Mono<Boolean> adopt(String id, String customerId) {
    return mongo
        .updateFirst(
            Query.query(Criteria.where(ID).is(id).and(OWNER).exists(false)),
            new Update().set(OWNER, customerId),
            CartDocument.class)
        .map(result -> result.getModifiedCount() == 1);
  }

  private Mono<Cart> attempt(String id, UnaryOperator<Cart> change) {
    return find(id)
        .flatMap(
            read -> {
              final Cart wanted = change.apply(read);
              return wanted.equals(read) ? Mono.just(read) : write(read, wanted);
            });
  }

  private Mono<Cart> write(Cart read, Cart wanted) {
    final Instant now = clock.instant();
    final Query query =
        Query.query(Criteria.where(ID).is(read.id().value()).and("version").is(read.version()));
    final Update update =
        new Update()
            .set("lines", wanted.lines().stream().map(CartMapping::toDocument).toList())
            .set("version", read.version() + 1)
            .set("updatedAt", now);
    return mongo
        .updateFirst(query, update, CartDocument.class)
        .flatMap(
            result ->
                result.getMatchedCount() == 1
                    ? Mono.just(
                        new Cart(
                            read.id(),
                            read.owner(),
                            wanted.lines(),
                            read.version() + 1,
                            read.createdAt(),
                            now))
                    : Mono.error(new OptimisticLockingFailureException("stale cart")));
  }

  private String newReference() {
    final byte[] bytes = new byte[REFERENCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
