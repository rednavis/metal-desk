package com.rednavis.metaldesk.share.domain.catalog;

/**
 * Whether a product can be bought now (BRD FR-1.3). The legacy catalog held a free-text {@code
 * stock} string that was in practice a warehouse country code; availability is a closed set here.
 */
public enum StockStatus {

  /** Available to order immediately. */
  IN_STOCK,

  /** Not available; the catalog still shows it. */
  OUT_OF_STOCK,

  /** Sourced on demand, so availability is confirmed after ordering. */
  ON_REQUEST
}
