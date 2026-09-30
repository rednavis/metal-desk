package com.rednavis.metaldesk.admin.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.admin.AdminTestSupport;
import com.rednavis.metaldesk.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TierEvaluation;
import com.rednavis.metaldesk.share.domain.fulfillment.TierSelector;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Tier configuration: validation, the overlap policy, warnings, and the no-restart guarantee. */
class TierApiTest extends AdminTestSupport {

  private static final String TIERS = "/api/admin/tiers";
  private static final String CODE = "$.code";
  private static final String WARNING_CODES = "$.warnings[*].code";
  private static final String PRICE_9_90 = "9.90";
  private static final String SMALL_CEILING = "1000";
  private static final String CHEAP = "8.00";
  private static final String DEFAULT_VALUE = "5000";
  private static final String DEFAULT_WEIGHT = "2000";

  @Autowired private FulfillmentTierMapper tierMapper;

  private static String body(
      String region, String value, String weight, String price, int minDays, int maxDays) {
    return ("{\"region\":\"%s\",\"valueCeiling\":\"%s\",\"weightCeiling\":\"%s\","
            + "\"weightUnit\":\"GRAM\",\"currency\":\"EUR\",\"deliveryPrice\":\"%s\","
            + "\"minDays\":%d,\"maxDays\":%d}")
        .formatted(region, value, weight, price, minDays, maxDays);
  }

  private static String standard(String region, String price) {
    return body(region, DEFAULT_VALUE, DEFAULT_WEIGHT, price, 2, 4);
  }

  private String createdId(String json) {
    final String response =
        Objects.requireNonNull(
            post(TIERS, json).expectBody(String.class).returnResult().getResponseBody());
    return response.replaceAll(".*\"tier\":\\{\"id\":\"([^\"]+)\".*", "$1");
  }

  private ResponseSpec rejected(String json, String code) {
    final ResponseSpec response = post(TIERS, json);
    response.expectStatus().isBadRequest().expectBody().jsonPath(CODE).isEqualTo(code);
    return response;
  }

  /** What checkout does: read the region's tiers from the database and select. */
  private TierEvaluation checkoutEvaluates(String region, String value, String grams) {
    final List<FulfillmentTier> found =
        tiers.findByRegion(region).stream().map(tierMapper::toDomain).toList();
    return TierSelector.select(
        new Region(region),
        Money.of(value, Currency.EUR),
        Weight.of(grams, WeightUnit.GRAM),
        found,
        NOW);
  }

  private TierEvaluation smallOrder() {
    return checkoutEvaluates("DE", "100", "50");
  }

  private static String priceOf(TierEvaluation evaluation) {
    return ((TierEvaluation.Priced) evaluation).quote().cost().amount().toPlainString();
  }

  @Test
  void newTierIsStoredAndListed() {
    post(TIERS, standard("DE", PRICE_9_90))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.tier.region")
        .isEqualTo("DE")
        .jsonPath("$.tier.deliveryPrice")
        .isEqualTo(PRICE_9_90)
        .jsonPath("$.warnings")
        .isEmpty();

    get(TIERS + "?region=de")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(1);
  }

  @Test
  void tierCreatedHereIsPricedByCheckoutWithNoRestart() {
    assertTrue(smallOrder() instanceof TierEvaluation.NoTier);

    final String id = createdId(standard("DE", PRICE_9_90));
    assertEquals(PRICE_9_90, priceOf(smallOrder()));

    put(TIERS + "/" + id, standard("DE", "14.50")).expectStatus().isOk();
    assertEquals("14.50", priceOf(smallOrder()));

    delete(TIERS + "/" + id).expectStatus().isOk();
    assertTrue(smallOrder() instanceof TierEvaluation.NoTier);
  }

  @Test
  void rejectsNonPositiveCeilings() {
    rejected(body("DE", "0", DEFAULT_WEIGHT, PRICE_9_90, 2, 4), "fulfillment-tier.ceiling-invalid");
    rejected(
        body("DE", "-5", DEFAULT_WEIGHT, PRICE_9_90, 2, 4), "fulfillment-tier.ceiling-invalid");
    rejected(body("DE", DEFAULT_VALUE, "0", PRICE_9_90, 2, 4), "fulfillment-tier.ceiling-invalid");
    rejected(body("DE", DEFAULT_VALUE, "-1", PRICE_9_90, 2, 4), "weight.negative");
  }

  @Test
  void rejectsNegativePrice() {
    rejected(
        body("DE", DEFAULT_VALUE, DEFAULT_WEIGHT, "-0.01", 2, 4), "fulfillment-tier.price-invalid");
  }

  @Test
  void rejectsNonPositiveTransitTime() {
    rejected(
        body("DE", DEFAULT_VALUE, DEFAULT_WEIGHT, PRICE_9_90, 0, 4),
        "transit-time.min-not-positive");
    rejected(
        body("DE", DEFAULT_VALUE, DEFAULT_WEIGHT, PRICE_9_90, 4, 2), "transit-time.range-inverted");
  }

  @Test
  void rejectsRegionThatDoesNotResolve() {
    rejected(standard("not a region!", PRICE_9_90), "region.malformed");
    rejected(standard(" ", PRICE_9_90), "region.blank");
  }

  @Test
  void rejectsIncompleteRequest() {
    rejected("{\"region\":\"DE\"}", "request.invalid");
    rejected(
        "{\"region\":null,\"valueCeiling\":null,\"weightCeiling\":null,\"weightUnit\":null,"
            + "\"currency\":null,\"deliveryPrice\":null,\"minDays\":2,\"maxDays\":4}",
        "tier.incomplete");
    post(TIERS, "not json").expectStatus().isBadRequest();
  }

  @Test
  void overlappingTiersAreAcceptedAndCheapestQualifyingWinsWhateverTheInsertionOrder() {
    post(TIERS, body("DE", SMALL_CEILING, "500", "20.00", 1, 2)).expectStatus().isCreated();
    post(TIERS, body("DE", DEFAULT_VALUE, DEFAULT_WEIGHT, CHEAP, 3, 5)).expectStatus().isCreated();
    // Either tier covers a small order; the cheaper one is taken, not the one entered first.
    assertEquals(CHEAP, priceOf(smallOrder()));

    tiers.deleteAll();
    post(TIERS, body("DE", DEFAULT_VALUE, DEFAULT_WEIGHT, CHEAP, 3, 5)).expectStatus().isCreated();
    post(TIERS, body("DE", SMALL_CEILING, "500", "20.00", 1, 2)).expectStatus().isCreated();
    assertEquals(CHEAP, priceOf(smallOrder()));
  }

  @Test
  void warnsWhenSavedTierCanNeverBeChosen() {
    post(TIERS, body("DE", DEFAULT_VALUE, DEFAULT_WEIGHT, CHEAP, 3, 5)).expectStatus().isCreated();

    post(TIERS, body("DE", SMALL_CEILING, "500", "20.00", 1, 2))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath(WARNING_CODES)
        .isEqualTo("tier.shadowed");
  }

  @Test
  void removingLastTierOfRegionWarns() {
    final String only = createdId(standard("DE", PRICE_9_90));
    final String first = createdId(standard("FR", PRICE_9_90));
    createdId(body("FR", "9000", "4000", "19.90", 2, 4));

    delete(TIERS + "/" + first).expectStatus().isOk().expectBody().jsonPath("$.warnings").isEmpty();
    delete(TIERS + "/" + only)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(WARNING_CODES)
        .isEqualTo("region.uncovered");
  }

  @Test
  void narrowingOrMovingLastTierWarns() {
    final String id = createdId(standard("DE", PRICE_9_90));

    put(TIERS + "/" + id, body("DE", SMALL_CEILING, DEFAULT_WEIGHT, PRICE_9_90, 2, 4))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(WARNING_CODES)
        .isEqualTo("region.narrowed");
    put(TIERS + "/" + id, body("AT", SMALL_CEILING, DEFAULT_WEIGHT, PRICE_9_90, 2, 4))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(WARNING_CODES)
        .isEqualTo("region.uncovered");
  }

  @Test
  void unknownTierIsNotFound() {
    get(TIERS + "/nope")
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("tier.not-found");
    delete(TIERS + "/nope").expectStatus().isNotFound();
  }
}
