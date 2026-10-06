package com.rednavis.metaldesk.api.account.verification;

import com.rednavis.metaldesk.mail.MailTemplate;

/**
 * What a verification challenge is for. A code issued for one purpose cannot confirm another, so a
 * password-reset code cannot verify an email address and the reverse.
 *
 * <p>The purpose only decides how the challenge looks: which mail it sends and how long and how
 * varied its code is. What happens after a successful confirmation belongs to the caller.
 */
public enum VerificationPurpose {

  /** Verifying the address given at registration (BRD FR-2.3). */
  REGISTRATION(MailTemplate.EMAIL_VERIFICATION, 6, false),

  /** Verifying the address given in quick registration at checkout (BRD FR-4.2, task T-035). */
  CHECKOUT_QUICK_REGISTRATION(MailTemplate.EMAIL_VERIFICATION, 6, false),

  /**
   * Proving control of the address to reset a password (BRD FR-2.4). The code travels in a link
   * rather than being typed, so it is long and unguessable.
   */
  PASSWORD_RESET(MailTemplate.PASSWORD_RESET, 32, true);

  private final MailTemplate mailTemplate;
  private final int length;
  private final boolean letters;

  VerificationPurpose(MailTemplate template, int codeLength, boolean alphanumeric) {
    this.mailTemplate = template;
    this.length = codeLength;
    this.letters = alphanumeric;
  }

  /**
   * The mail that carries the code.
   *
   * @return the template
   */
  public MailTemplate template() {
    return mailTemplate;
  }

  /**
   * How many characters the code has.
   *
   * @return the length
   */
  public int codeLength() {
    return length;
  }

  /**
   * Whether the code uses letters as well as digits.
   *
   * @return {@code true} for a long link token, {@code false} for a short numeric code
   */
  public boolean alphanumeric() {
    return letters;
  }
}
