package com.rednavis.metaldesk.share.domain;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A delivery region: one of the three inputs to fulfillment-tier lookup (BRD FR-5.1).
 *
 * <p><strong>Decision: a record wrapping a code, not an enum.</strong> FR-5.1 has staff configure
 * fulfillment tiers <em>per region</em>, so the set of regions is staff-managed data. An enum would
 * force a redeploy to serve a new country — the same mistake Architecture section 3 calls out for
 * {@code FulfillmentTier}, and the mistake the legacy {@code RegionType} (EU, DE, OTHER) made. The
 * set of regions that actually exist is therefore validated where tiers are stored, not here.
 *
 * <p>The code is trimmed and upper-cased at construction so {@code "eu-core"} equals {@code
 * "EU-CORE"}. It must start with a letter or digit and may then contain letters, digits, {@code _}
 * and {@code -}, up to 32 characters.
 *
 * @param code the normalised region code
 */
public record Region(String code) {

  private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,31}");

  /**
   * Validates and normalises the code.
   *
   * @throws ValidationException if the code is null, blank or malformed
   */
  public Region {
    if (code == null || code.isBlank()) {
      throw new ValidationException("region.blank", "Region code must not be null or blank");
    }
    code = code.strip().toUpperCase(Locale.ROOT);
    if (!FORMAT.matcher(code).matches()) {
      throw new ValidationException("region.malformed", "Malformed region code: " + code);
    }
  }
}
