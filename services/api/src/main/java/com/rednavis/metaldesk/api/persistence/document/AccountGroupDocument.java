package com.rednavis.metaldesk.api.persistence.document;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A set of accounts one person may switch between without signing in again (BRD FR-2.5).
 *
 * <p>Membership is symmetric: every member may act as every other. That is what lets a stateless
 * token switch account and then switch back.
 *
 * @param id the group id
 * @param members the customer ids in the group; a customer is in at most one group
 */
@Document("account_groups")
public record AccountGroupDocument(@Id String id, @Indexed List<String> members) {

  /** Copies the list, so the document cannot be changed through it. */
  public AccountGroupDocument {
    members = members == null ? List.of() : List.copyOf(members);
  }
}
