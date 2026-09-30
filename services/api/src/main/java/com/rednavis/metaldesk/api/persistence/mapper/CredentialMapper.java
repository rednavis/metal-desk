package com.rednavis.metaldesk.api.persistence.mapper;

import com.rednavis.metaldesk.api.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.share.domain.customer.AuthCredential;
import com.rednavis.metaldesk.share.domain.customer.AuthIdentifier;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import org.springframework.stereotype.Component;

/** Maps an {@link AuthCredential} to its {@link CredentialDocument} and back. */
@Component
public class CredentialMapper {

  /**
   * Rebuilds the domain credential.
   *
   * <p>The identifier is not stored on the credential, so the caller supplies the one it signed in
   * with.
   *
   * @param document the stored credential
   * @param identifier the identifier the customer was found by
   * @return the credential
   */
  public AuthCredential toDomain(CredentialDocument document, AuthIdentifier identifier) {
    return new AuthCredential(identifier, document.passwordHash(), document.state());
  }

  /**
   * Builds the document to store.
   *
   * @param customerId the customer the credential belongs to
   * @param credential the credential
   * @return the document
   */
  public CredentialDocument toDocument(CustomerId customerId, AuthCredential credential) {
    return new CredentialDocument(
        customerId.value(), credential.passwordHash(), credential.state());
  }
}
