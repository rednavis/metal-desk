package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import com.rednavis.metaldesk.mail.fake.RecordedMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads verification codes and reset links out of the mail the in-process sender recorded. */
public final class MailInspector {

  private static final Pattern LINK_CODE = Pattern.compile("reference=([^&\\s]+)&code=(\\S+)");
  private static final Pattern TYPED_CODE = Pattern.compile("\\b(\\d{6})\\b");

  private MailInspector() {}

  /**
   * The mail sent to an address.
   *
   * @param sender the recording sender
   * @param address the recipient
   * @return the mail, oldest first
   */
  public static List<TransactionalMail> to(InProcessMailSender sender, String address) {
    return sender.sentTo(new EmailAddress(address)).stream().map(RecordedMail::mail).toList();
  }

  /**
   * The typed code in a verification mail.
   *
   * @param mail the mail
   * @return the six-digit code
   */
  public static String typedCode(TransactionalMail mail) {
    final Matcher matcher = TYPED_CODE.matcher(mail.body());
    if (!matcher.find()) {
      throw new IllegalStateException("No typed code in the mail");
    }
    return matcher.group(1);
  }

  /**
   * The reference in a reset link.
   *
   * @param mail the mail
   * @return the reference
   */
  public static String linkReference(TransactionalMail mail) {
    return link(mail).group(1);
  }

  /**
   * The code in a reset link.
   *
   * @param mail the mail
   * @return the code
   */
  public static String linkCode(TransactionalMail mail) {
    return link(mail).group(2);
  }

  private static Matcher link(TransactionalMail mail) {
    final Matcher matcher = LINK_CODE.matcher(mail.body());
    if (!matcher.find()) {
      throw new IllegalStateException("No link in the mail");
    }
    return matcher;
  }
}
