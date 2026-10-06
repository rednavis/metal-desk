package com.rednavis.metaldesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rednavis.metaldesk.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.persistence.document.PriceDocument.ScopeKind;
import com.rednavis.metaldesk.persistence.document.PriceRuleDocument;
import com.rednavis.metaldesk.persistence.mapper.CredentialMapper;
import com.rednavis.metaldesk.persistence.mapper.PriceRuleMapper;
import com.rednavis.metaldesk.share.domain.customer.AuthCredential;
import com.rednavis.metaldesk.share.domain.customer.AuthIdentifier;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The price-rule and credential mappers round-trip, for both scopes and both credential states. */
class PriceRuleAndCredentialMapperTest {

  private final PriceRuleMapper rules = new PriceRuleMapper();
  private final CredentialMapper credentials = new CredentialMapper();

  @Test
  void productRuleRoundTrips() {
    final PriceRule rule =
        new PriceRule(
            new Margin(new BigDecimal("12.5")), new PriceRule.Scope.ForProduct(new ProductId("p")));

    final PriceRuleDocument document = rules.toDocument(rule);

    assertEquals(ScopeKind.PRODUCT, document.scopeKind());
    assertEquals("PRODUCT:p", document.id());
    assertEquals(rule, rules.toDomain(document));
  }

  @Test
  void categoryRuleRoundTrips() {
    final PriceRule rule =
        new PriceRule(
            new Margin(new BigDecimal("3")), new PriceRule.Scope.ForCategory(new CategoryId("c")));

    final PriceRuleDocument document = rules.toDocument(rule);

    assertEquals(ScopeKind.CATEGORY, document.scopeKind());
    assertEquals("c", document.scopeId());
    assertEquals(rule, rules.toDomain(document));
  }

  @Test
  void activeCredentialKeepsItsHashAndState() {
    assertCredentialRoundTrips(AuthCredential.State.ACTIVE);
  }

  @Test
  void disabledCredentialKeepsItsHashAndState() {
    assertCredentialRoundTrips(AuthCredential.State.DISABLED);
  }

  private void assertCredentialRoundTrips(AuthCredential.State state) {
    final AuthIdentifier identifier = new AuthIdentifier.Email(new EmailAddress("a@b.example"));
    final AuthCredential credential = new AuthCredential(identifier, "hash", state);

    final CredentialDocument document = credentials.toDocument(new CustomerId("c-1"), credential);

    assertEquals("c-1", document.id());
    assertEquals(credential, credentials.toDomain(document, identifier));
  }
}
