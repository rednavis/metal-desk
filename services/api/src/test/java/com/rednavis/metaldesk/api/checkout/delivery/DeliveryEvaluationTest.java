package com.rednavis.metaldesk.api.checkout.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tier evaluation on live configuration (BRD FR-5.1 to FR-5.3, BR-8, BR-10). */
class DeliveryEvaluationTest extends DeliveryTestSupport {

  private static final String WIDE = "10000.00";
  private static final String HEAVY = "5000";
  private static final String PRICE = "15.00";
  private static final String AMOUNT = "amount";

  private static final String STAGE = "stage";
  private static final String REASON = "reason";
  private static final String ALLOWED = "PAYMENT_ALLOWED";
  private static final String HANDOFF_REQUIRED = "HANDOFF_REQUIRED";
  private static final String BOUND = "boundCeiling";
  private static final String QUOTE = "quote";

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  @Test
  void basketInsideBothCeilingsIsPricedAutomaticallyFromTheTier() {
    configureTier("AT", WIDE, HEAVY, PRICE);
    final String id = checkoutTo("AT", GOLD_1, 1);

    final Called called = evaluate(id);

    assertEquals(200, called.status());
    assertEquals(ALLOWED, called.body().get(STAGE));
    final Map<?, ?> quote = (Map<?, ?>) called.body().get(QUOTE);
    assertEquals("tier-AT", quote.get("tierId"));
    assertEquals(PRICE, ((Map<?, ?>) quote.get("cost")).get(AMOUNT));
    assertEquals(2, quote.get("minDays"));
    assertNull(called.body().get(REASON));
    assertNull(called.body().get(BOUND));
    assertEquals("6299.37", ((Map<?, ?>) called.body().get("exTaxValue")).get(AMOUNT));
    assertEquals("100", called.body().get("weightGrams"));
  }

  @Test
  void exceedingTheValueCeilingIsHandoffNamingTheValueCeiling() {
    configureTier("CH", "5000.00", HEAVY, PRICE);

    final Called called = evaluate(checkoutTo("CH", GOLD_1, 1));

    assertEquals(HANDOFF_REQUIRED, called.body().get(STAGE));
    assertEquals("VALUE_CEILING_EXCEEDED", called.body().get(REASON));
    assertEquals("VALUE", called.body().get(BOUND));
    assertNull(called.body().get(QUOTE), "never a quote for an order a manager must price");
  }

  @Test
  void exceedingTheWeightCeilingIsHandoffNamingTheWeightCeiling() {
    configureTier("FR", "1000000.00", "250", PRICE);

    final Called called = evaluate(checkoutTo("FR", GOLD_1, 3));

    assertEquals(HANDOFF_REQUIRED, called.body().get(STAGE));
    assertEquals("WEIGHT_CEILING_EXCEEDED", called.body().get(REASON));
    assertEquals("WEIGHT", called.body().get(BOUND));
    assertEquals("300", called.body().get("weightGrams"));
  }

  @Test
  void theValueBeforeTaxIsWhatIsEvaluatedNotTheGrossTotal() {
    // Net 6599.34, gross at 19 % 7853.21: a ceiling of 7000 sits between them.
    configureTier("IT", "7000.00", HEAVY, PRICE);

    final Called called = evaluate(checkoutTo("IT", TAXED, 1));

    assertEquals(ALLOWED, called.body().get(STAGE), "the net is under the ceiling");
    assertEquals("6599.34", ((Map<?, ?>) called.body().get("exTaxValue")).get(AMOUNT));
  }

  @Test
  void regionWithNoTierIsHandoffWithItsOwnReasonNeverFreeQuote() {
    removeTiers("ES");

    final Called called = evaluate(checkoutTo("ES", GOLD_1, 1));

    assertEquals(HANDOFF_REQUIRED, called.body().get(STAGE));
    assertEquals("NO_TIER_FOR_REGION", called.body().get(REASON));
    assertNull(called.body().get(BOUND));
    assertNull(called.body().get(QUOTE));
  }

  @Test
  void editingTierBetweenTwoEvaluationsChangesTheSecondResult() {
    configureTier("NL", WIDE, HEAVY, PRICE);
    final String id = checkoutTo("NL", GOLD_1, 1);
    assertEquals(ALLOWED, evaluate(id).body().get(STAGE));

    configureTier("NL", "1000.00", HEAVY, PRICE);
    final Called tightened = evaluate(id);
    configureTier("NL", WIDE, HEAVY, "20.00");
    final Called loosened = evaluate(id);

    assertEquals(HANDOFF_REQUIRED, tightened.body().get(STAGE));
    assertEquals(ALLOWED, loosened.body().get(STAGE));
    assertEquals(
        "20.00", ((Map<?, ?>) ((Map<?, ?>) loosened.body().get(QUOTE)).get("cost")).get(AMOUNT));
  }

  @Test
  void theCheapestOfSeveralAcceptingTiersWins() {
    configureTier("BE", WIDE, HEAVY, "30.00");
    // A second, cheaper tier for the same region.
    final String id = checkoutTo("BE", GOLD_1, 1);
    configureSecondTier("BE");

    final Called called = evaluate(id);

    assertEquals("tier-BE-cheap", ((Map<?, ?>) called.body().get(QUOTE)).get("tierId"));
  }

  private void configureSecondTier(String region) {
    configureTierKeeping(region, "tier-" + region + "-cheap", "9000.00", HEAVY, "9.00");
  }

  @Test
  void theEvaluationIsRecordedOnTheSession() {
    configureTier("PL", WIDE, HEAVY, PRICE);
    final String id = checkoutTo("PL", GOLD_1, 1);
    evaluate(id);

    final Called stored = session(id, null);

    assertNotNull(stored.body().get("delivery"));
    assertEquals(ALLOWED, ((Map<?, ?>) stored.body().get("delivery")).get(STAGE));
  }

  @Test
  void evaluatingBeforeStepOneIsCompleteIs409() {
    final String id = checkoutId(startFromCart(null));

    final Called called = evaluate(id);

    assertEquals(409, called.status());
    assertEquals("checkout.step1-incomplete", called.body().get("code"));
  }

  @Test
  void unknownSessionIs404() {
    assertEquals(404, evaluate("no-such-session").status());
  }

  @Test
  void changingTheAddressDropsTheStaleEvaluation() {
    configureTier("SE", WIDE, HEAVY, PRICE);
    final String id = checkoutTo("SE", GOLD_1, 1);
    evaluate(id);

    submit(id, null, with(validForm(freshEmail()), "country", "SE"));

    assertNull(session(id, null).body().get("delivery"));
  }
}
