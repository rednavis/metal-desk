package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.checkout.CheckoutTestSupport;
import com.rednavis.metaldesk.api.persistence.repository.FulfillmentTierRepository;
import com.rednavis.metaldesk.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * The base of the delivery tests: configures a fulfillment tier for a region and drives a checkout
 * through step 1 to a delivery evaluation. Each test uses a region of its own, because the tiers
 * live in a database the tests share.
 */
public class DeliveryTestSupport extends CheckoutTestSupport {

  /** The path suffix of a delivery evaluation. */
  protected static final String EVALUATE_PATH = "/delivery/evaluate";

  /** The path suffix of a manager handoff. */
  protected static final String HANDOFF_PATH = "/handoff";

  @Autowired private FulfillmentTierRepository tierRepo;
  @Autowired private FulfillmentTierMapper tierMapper;

  /** Creates the base; subclasses are the tests. */
  protected DeliveryTestSupport() {
    super();
  }

  /**
   * Makes a region have exactly one tier.
   *
   * @param region the two-letter region code
   * @param valueCeiling the highest ex-tax order value in euro
   * @param weightGrams the heaviest order in grams
   * @param price the delivery price in euro
   */
  protected void configureTier(
      String region, String valueCeiling, String weightGrams, String price) {
    removeTiers(region);
    tierRepo
        .save(
            tierMapper.toDocument(
                new FulfillmentTier(
                    new FulfillmentTierId("tier-" + region),
                    new Region(region),
                    Money.of(valueCeiling, Currency.EUR),
                    Weight.of(weightGrams, WeightUnit.GRAM),
                    Money.of(price, Currency.EUR),
                    new TransitTime(2, 4))))
        .block();
  }

  /**
   * Adds a further tier to a region, keeping the ones it has.
   *
   * @param region the two-letter region code
   * @param id the new tier's id
   * @param valueCeiling the highest ex-tax order value in euro
   * @param weightGrams the heaviest order in grams
   * @param price the delivery price in euro
   */
  protected void configureTierKeeping(
      String region, String id, String valueCeiling, String weightGrams, String price) {
    tierRepo
        .save(
            tierMapper.toDocument(
                new FulfillmentTier(
                    new FulfillmentTierId(id),
                    new Region(region),
                    Money.of(valueCeiling, Currency.EUR),
                    Weight.of(weightGrams, WeightUnit.GRAM),
                    Money.of(price, Currency.EUR),
                    new TransitTime(1, 2))))
        .block();
  }

  /**
   * Removes every tier of a region.
   *
   * @param region the two-letter region code
   */
  protected void removeTiers(String region) {
    tierRepo.deleteAll(tierRepo.findByRegion(region)).block();
  }

  /**
   * Starts a guest checkout with a product in the cart, completes step 1 for a region, and returns
   * the session id.
   *
   * @param region the destination country code
   * @param product the product to buy
   * @param quantity how many
   * @return the checkout id
   */
  protected String checkoutTo(String region, String product, int quantity) {
    final Called cart = add(null, null, product);
    changeQuantity(cart.cookie(), product, quantity);
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), null, null));
    final Called submitted = submit(id, null, with(validForm(freshEmail()), "country", region));
    if (!submitted.body().containsKey("step1Complete")) {
      throw new IllegalStateException("step 1 failed: " + submitted.body());
    }
    return id;
  }

  /**
   * Evaluates the delivery of a session.
   *
   * @param checkoutId the session id
   * @return the response
   */
  protected Called evaluate(String checkoutId) {
    return call(HttpMethod.POST, SESSIONS + "/" + checkoutId + EVALUATE_PATH, null, null, null);
  }

  /**
   * Hands a session to staff.
   *
   * @param checkoutId the session id
   * @param body the request body, or null
   * @return the response
   */
  protected Called handoff(String checkoutId, Map<String, Object> body) {
    return call(HttpMethod.POST, SESSIONS + "/" + checkoutId + HANDOFF_PATH, null, null, body);
  }
}
