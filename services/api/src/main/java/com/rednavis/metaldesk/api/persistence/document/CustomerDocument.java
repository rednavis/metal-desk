package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer as stored.
 *
 * <p>Email is unique, because an account is found and signed into by it; the domain has already
 * lower-cased it. Phone is a sign-in identifier too, but optional, so its index is sparse and not
 * unique: uniqueness of a phone number is a rule for the account-lifecycle task to decide.
 *
 * @param id the customer id
 * @param name the customer's name
 * @param email the normalised email address, unique
 * @param phone the normalised phone number, or null
 * @param addresses the saved addresses
 * @param verification whether the customer has verified their contact details
 */
@Document("customers")
public record CustomerDocument(
    @Id String id,
    String name,
    @Indexed(unique = true) String email,
    @Indexed(sparse = true) String phone,
    List<AddressDocument> addresses,
    VerificationState verification) {

  /** Copies the list, so the document cannot be changed through it; an absent list is empty. */
  public CustomerDocument {
    addresses = addresses == null ? List.of() : List.copyOf(addresses);
  }
}
