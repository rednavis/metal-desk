/**
 * Fulfillment-tier configuration (BRD FR-5.1). Tiers are data, not code (Architecture section 3):
 * checkout reads them from the database on every evaluation, so a change made here applies to the
 * next evaluation with no restart and no cache to flush.
 */
package com.rednavis.metaldesk.admin.tier;
