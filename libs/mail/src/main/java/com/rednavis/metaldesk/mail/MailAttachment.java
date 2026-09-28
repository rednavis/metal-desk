package com.rednavis.metaldesk.mail;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A file attached to a mail: its name, its content type and its bytes. It carries the invoice PDF
 * (BRD FR-6.2), which is rendered elsewhere; this only attaches it.
 *
 * <p>The file name and content type end up in mail headers, so they are held to a narrow shape: a
 * file name has no path separator, control character or line break, and a content type is {@code
 * type/subtype}. That keeps an attachment from carrying a path or from injecting a header. The
 * bytes are copied on the way in and out, equality is by content, and the string form shows the
 * size, never the bytes.
 *
 * @param filename the file name, never blank, at most 255 characters and free of path separators
 *     and control characters
 * @param contentType the media type in lower case, such as {@code application/pdf}
 * @param bytes the file's bytes, never empty; the record holds and returns copies
 */
public record MailAttachment(String filename, String contentType, byte[] bytes) {

  private static final int MAX_NAME_LENGTH = 255;
  private static final Pattern FILENAME = Pattern.compile("[^/\\\\\\p{Cntrl}]+");
  private static final Pattern CONTENT_TYPE =
      Pattern.compile("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*");

  /**
   * Validates the fields and copies the bytes.
   *
   * @throws ValidationException if the file name or content type is malformed, or the bytes are
   *     missing or empty
   */
  public MailAttachment {
    if (filename == null
        || filename.isBlank()
        || filename.length() > MAX_NAME_LENGTH
        || !FILENAME.matcher(filename).matches()) {
      throw new ValidationException(
          "mail-attachment.filename-invalid",
          "Attachment file name must be 1 to 255 characters without path separators or controls");
    }
    contentType = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
    if (!CONTENT_TYPE.matcher(contentType).matches()) {
      throw new ValidationException(
          "mail-attachment.content-type-invalid", "Attachment content type must be type/subtype");
    }
    if (bytes == null || bytes.length == 0) {
      throw new ValidationException("mail-attachment.bytes-empty", "Attachment must have content");
    }
    bytes = bytes.clone();
  }

  /**
   * Returns a copy of the file's bytes.
   *
   * @return the bytes
   */
  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MailAttachment that
        && filename.equals(that.filename)
        && contentType.equals(that.contentType)
        && Arrays.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filename, contentType, Arrays.hashCode(bytes));
  }

  @Override
  public String toString() {
    return "MailAttachment[" + filename + ", " + contentType + ", " + bytes.length + " bytes]";
  }
}
