/**
 * Manager quotes (BRD FR-5.3): the orders the checkout handed off because they exceed every tier,
 * and the two things staff can do with them. Setting terms fires {@code QUOTE_SET} and returns the
 * order to awaiting payment with the human-decided delivery price; declining fires {@code
 * QUOTE_DECLINED} and cancels it.
 */
package com.rednavis.metaldesk.admin.quote;
