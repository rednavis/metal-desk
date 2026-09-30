package com.rednavis.metaldesk.admin.tier.dto;

import java.util.List;

/**
 * The outcome of deleting a tier.
 *
 * @param removedId the id of the tier that was removed
 * @param warnings what the removal implies for checkout; empty if nothing
 */
public record TierRemoval(String removedId, List<TierWarning> warnings) {

  /** Copies the list, so the view cannot be changed through it. */
  public TierRemoval {
    warnings = List.copyOf(warnings);
  }
}
