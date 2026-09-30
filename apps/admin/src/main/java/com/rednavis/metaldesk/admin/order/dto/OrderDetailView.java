package com.rednavis.metaldesk.admin.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import java.time.Instant;
import java.util.List;

/**
 * One order in full.
 *
 * @param summary the order as listed
 * @param customerName the customer's name, or null if the customer record is gone
 * @param destination the delivery address as one line
 * @param lines what was ordered
 * @param net the net of all lines
 * @param tax the tax of all lines
 * @param delivery the delivery cost, zero until a quote exists
 * @param quote the delivery quote, absent until one exists
 * @param shipment the shipment, absent until one was entered
 * @param actions the triggers the state machine accepts now, for the UI to offer
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetailView(
    OrderSummaryView summary,
    String customerName,
    String destination,
    List<LineView> lines,
    String net,
    String tax,
    String delivery,
    QuoteInfo quote,
    ShipmentView shipment,
    List<TransitionTrigger> actions) {

  /** Copies the lists, so the view cannot be changed through them. */
  public OrderDetailView {
    lines = List.copyOf(lines);
    actions = List.copyOf(actions);
  }

  /**
   * One order line.
   *
   * @param productId the product
   * @param name the product name at the time of ordering
   * @param quantity how many
   * @param unitPrice the sellable unit price, as decimal text
   * @param lineNet the net of the line
   * @param lineTax the tax of the line
   */
  public record LineView(
      String productId,
      String name,
      int quantity,
      String unitPrice,
      String lineNet,
      String lineTax) {}

  /**
   * The delivery quote of an order.
   *
   * @param tierId the tier that priced it, or the manager-quote marker
   * @param cost the delivery cost, as decimal text
   * @param minDays the fewest delivery days
   * @param maxDays the most delivery days
   * @param quotedAt when it was quoted
   */
  public record QuoteInfo(String tierId, String cost, int minDays, int maxDays, Instant quotedAt) {}

  /**
   * The shipment of an order (BRD FR-10.1).
   *
   * @param carrier who carries it
   * @param trackingReference how to follow it
   */
  public record ShipmentView(String carrier, String trackingReference) {}
}
