package com.rednavis.metaldesk.api.inquiry;

/** Where an inquiry came from (BRD FR-9.1, FR-9.2). */
public enum InquirySource {

  /** A price inquiry from the catalog listing. */
  CATALOG,

  /** A price inquiry from a product page, about that product. */
  PRODUCT,

  /** A question about an order that was handed to a manager because it exceeded a ceiling. */
  HANDOFF
}
