package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.persistence.document.AccountGroupDocument;
import com.rednavis.metaldesk.api.persistence.repository.AccountGroupRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Answers "which customers may this principal act as" (BRD FR-2.5).
 *
 * <p>Accounts a person can switch between form a <em>group</em>, and membership is symmetric: every
 * member may act as every other. That is what lets a stateless token switch to another account and
 * switch back again, with no server-side session remembering who started. A customer in no group
 * can act only as themselves.
 *
 * <p>Creating links is a privileged operation (it must prove control of both accounts) and is not
 * exposed over HTTP by this task; {@link #link} is the operation a later flow or a support tool
 * would call.
 */
@Service
@RequiredArgsConstructor
public class AccountAccessService {

  private final AccountGroupRepository groups;

  /**
   * Lists the customers a principal may act as, itself included.
   *
   * @param customerId the principal's customer id
   * @return the customer ids
   */
  public Mono<Set<String>> accessibleTo(String customerId) {
    return groups
        .findFirstByMembersContaining(customerId)
        .map(group -> Set.copyOf(group.members()))
        .defaultIfEmpty(Set.of(customerId));
  }

  /**
   * Tells whether a principal may act as a target.
   *
   * @param principalId the principal's customer id
   * @param targetId the customer id it wants to act as
   * @return whether the target is the principal itself or in its group
   */
  public Mono<Boolean> mayActAs(String principalId, String targetId) {
    return accessibleTo(principalId).map(accessible -> accessible.contains(targetId));
  }

  /**
   * Puts two accounts in one group, merging their groups if both already have one.
   *
   * @param first one customer id
   * @param second the other customer id
   * @return a signal that completes when they are linked
   */
  public Mono<Void> link(String first, String second) {
    return groupOf(first)
        .zipWith(groupOf(second))
        .flatMap(pair -> merge(first, second, pair.getT1(), pair.getT2()));
  }

  private Mono<Optional<AccountGroupDocument>> groupOf(String customerId) {
    return groups
        .findFirstByMembersContaining(customerId)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty());
  }

  private Mono<Void> merge(
      String first,
      String second,
      Optional<AccountGroupDocument> firstGroup,
      Optional<AccountGroupDocument> secondGroup) {
    final Set<String> members = new LinkedHashSet<>(List.of(first, second));
    firstGroup.ifPresent(group -> members.addAll(group.members()));
    secondGroup.ifPresent(group -> members.addAll(group.members()));
    final String id =
        firstGroup
            .or(() -> secondGroup)
            .map(AccountGroupDocument::id)
            .orElseGet(() -> UUID.randomUUID().toString());
    final boolean separate =
        firstGroup.isPresent()
            && secondGroup.isPresent()
            && !firstGroup.get().id().equals(secondGroup.get().id());
    final Mono<Void> dropSecond =
        separate ? groups.deleteById(secondGroup.get().id()) : Mono.empty();
    return dropSecond.then(groups.save(new AccountGroupDocument(id, List.copyOf(members)))).then();
  }
}
