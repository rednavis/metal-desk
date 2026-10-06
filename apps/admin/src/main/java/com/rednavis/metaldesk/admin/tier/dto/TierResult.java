package com.rednavis.metaldesk.admin.tier.dto;

import java.util.List;

/**
 * The outcome of creating or changing a tier.
 *
 * @param tier the tier as stored
 * @param warnings what the change implies for checkout; empty if nothing
 */
public record TierResult(TierView tier, List<TierWarning> warnings) {

  /** Copies the list, so the view cannot be changed through it. */
  public TierResult {
    warnings = List.copyOf(warnings);
  }
}
