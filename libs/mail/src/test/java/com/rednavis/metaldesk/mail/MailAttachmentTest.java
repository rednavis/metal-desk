package com.rednavis.metaldesk.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MailAttachmentTest {

  private static final byte[] PDF = "%PDF-test".getBytes(StandardCharsets.US_ASCII);

  private static MailAttachment attachment(byte[] bytes) {
    return new MailAttachment("invoice.pdf", "application/pdf", bytes);
  }

  @Test
  void bytesAreCopiedInAndOut() {
    final byte[] original = PDF.clone();
    final MailAttachment attachment = attachment(original);
    original[0] = 0;
    final byte[] returned = attachment.bytes();
    returned[1] = 0;
    assertEquals('%', attachment.bytes()[0]);
    assertEquals('P', attachment.bytes()[1]);
  }

  @Test
  void equalityIsByContentAndTheStringFormHidesTheBytes() {
    assertEquals(attachment(PDF), attachment(PDF.clone()));
    assertNotEquals(attachment(PDF), attachment("%PDF-other".getBytes(StandardCharsets.US_ASCII)));
    assertEquals(
        "MailAttachment[invoice.pdf, application/pdf, 9 bytes]", attachment(PDF).toString());
  }

  @Test
  void contentTypeIsNormalisedToLowerCase() {
    assertEquals(
        "application/pdf",
        new MailAttachment("a.pdf", " Application/PDF ", PDF.clone()).contentType());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {" ", "../secret.pdf", "dir/invoice.pdf", "back\\slash.pdf", "line\nbreak.pdf"})
  void unsafeFileNamesAreRefused(String filename) {
    assertEquals(
        "mail-attachment.filename-invalid",
        assertThrows(
                ValidationException.class,
                () -> new MailAttachment(filename, "application/pdf", PDF.clone()))
            .code());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"pdf", "application/", "/pdf", "application/pdf\nBcc: x", "text plain/x"})
  void malformedContentTypesAreRefused(String contentType) {
    assertEquals(
        "mail-attachment.content-type-invalid",
        assertThrows(
                ValidationException.class,
                () -> new MailAttachment("invoice.pdf", contentType, PDF.clone()))
            .code());
  }

  @Test
  void emptyOrMissingBytesAreRefused() {
    assertEquals(
        "mail-attachment.bytes-empty",
        assertThrows(ValidationException.class, () -> attachment(new byte[0])).code());
    assertEquals(
        "mail-attachment.bytes-empty",
        assertThrows(ValidationException.class, () -> attachment(null)).code());
  }
}
