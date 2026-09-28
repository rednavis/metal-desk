package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The structure of the minimal PDF the invoice renderer builds on. */
class PdfWriterTest {

  private static final Charset WIN_ANSI = Charset.forName("windows-1252");

  private static String pdf(List<String> lines) {
    return new String(PdfWriter.write(lines), WIN_ANSI);
  }

  @Test
  void startsWithPdfHeaderAndEndsWithTheEndOfFileMarker() {
    final byte[] bytes = PdfWriter.write(List.of("hello"));
    assertEquals("%PDF-1.4", new String(bytes, 0, 8, StandardCharsets.US_ASCII));
    assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).endsWith("%%EOF\n"));
  }

  @Test
  void crossReferenceOffsetsPointAtTheirObjects() {
    final String pdf = pdf(List.of("one", "two"));
    final int startxref = pdf.lastIndexOf("startxref\n") + "startxref\n".length();
    final int xrefOffset = Integer.parseInt(pdf.substring(startxref, pdf.indexOf('\n', startxref)));
    assertTrue(pdf.startsWith("xref", xrefOffset));
    final Matcher entries = Pattern.compile("([0-9]{10}) 00000 n ").matcher(pdf);
    int objectNumber = 0;
    while (entries.find()) {
      objectNumber++;
      assertTrue(
          pdf.startsWith(objectNumber + " 0 obj", Integer.parseInt(entries.group(1))),
          "object " + objectNumber);
    }
    assertEquals(5, objectNumber, "catalog, pages, font, one page and its content");
  }

  @Test
  void everyCrossReferenceEntryIsTwentyBytes() {
    final String pdf = pdf(List.of("one"));
    final String table = pdf.substring(pdf.indexOf("0000000000 65535 f "), pdf.indexOf("trailer"));
    assertEquals(0, table.length() % 20);
  }

  @Test
  void longInputIsPaginatedNotClipped() {
    final List<String> lines = new ArrayList<>();
    for (int index = 0; index < 100; index++) {
      lines.add("line " + index);
    }
    final String pdf = pdf(lines);
    assertTrue(pdf.contains("/Count 3"));
    assertTrue(pdf.contains("(line 99)"));
  }

  @Test
  void noInputStillGivesOneBlankPage() {
    assertTrue(pdf(List.of()).contains("/Count 1"));
  }

  @Test
  void specialCharactersAreEscapedAndEncoded() {
    final String pdf = pdf(List.of("Bar (100 g) \\ Ü € 中"));
    assertTrue(pdf.contains("Bar \\(100 g\\) \\\\ Ü € ?"));
  }

  @Test
  void theSameLinesGiveTheSameBytes() {
    assertArrayEquals(PdfWriter.write(List.of("a", "b")), PdfWriter.write(List.of("a", "b")));
  }
}
