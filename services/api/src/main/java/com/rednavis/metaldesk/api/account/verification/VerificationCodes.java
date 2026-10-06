package com.rednavis.metaldesk.api.account.verification;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates verification codes and challenge references from a secure random source.
 *
 * <p>Codes avoid look-alike characters so a link token typed by hand survives; references are
 * opaque and are not secrets on their own.
 */
@Component
public class VerificationCodes {

  private static final String DIGITS = "0123456789";
  private static final String ALPHANUMERIC =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  private static final int REFERENCE_BYTES = 16;

  private final SecureRandom random = new SecureRandom();

  /**
   * Generates a code for a purpose.
   *
   * @param purpose the purpose, which decides length and alphabet
   * @return the code
   */
  public String code(VerificationPurpose purpose) {
    final String alphabet = purpose.alphanumeric() ? ALPHANUMERIC : DIGITS;
    final StringBuilder code = new StringBuilder(purpose.codeLength());
    for (int i = 0; i < purpose.codeLength(); i++) {
      code.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return code.toString();
  }

  /**
   * Generates an opaque reference.
   *
   * @return a URL-safe random reference
   */
  public String reference() {
    final byte[] bytes = new byte[REFERENCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
