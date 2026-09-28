package com.rednavis.metaldesk.payments.invoice;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes lines of plain text as a minimal, valid PDF 1.4 file, with no library.
 *
 * <p>This is deliberately small: fixed A4 pages, one built-in font, one column of text, and a new
 * page every {@value #LINES_PER_PAGE} lines so a long order is never clipped. It is enough for the
 * invoice's content; layout, branding and typography are out of scope.
 *
 * <p>The text is encoded in Windows-1252, which the file declares (WinAnsi), so euro signs and
 * accented letters such as German umlauts survive. A character outside that set becomes a question
 * mark rather than corrupting the file. The output is deterministic: no date, no random identifier,
 * so the same lines always give the same bytes.
 */
final class PdfWriter {

  private static final int LINES_PER_PAGE = 48;
  private static final Charset WIN_ANSI = Charset.forName("windows-1252");
  private static final int FIRST_PAGE_OBJECT = 4;

  private PdfWriter() {}

  /**
   * Writes the lines as a PDF.
   *
   * @param lines the text lines, top to bottom; an empty list gives one blank page
   * @return the bytes of the file
   */
  /* default */ static byte[] write(List<String> lines) {
    final List<List<String>> pages = paginate(lines);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final List<Integer> offsets = new ArrayList<>();
    writeAscii(out, "%PDF-1.4\n");
    writeObject(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
    writeObject(out, offsets, pagesObject(pages.size()));
    writeObject(
        out,
        offsets,
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
    for (int index = 0; index < pages.size(); index++) {
      final int content = FIRST_PAGE_OBJECT + 2 * index + 1;
      writeObject(
          out,
          offsets,
          "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R"
              + " >> >> /Contents "
              + content
              + " 0 R >>");
      writeStream(out, offsets, pageText(pages.get(index)));
    }
    final int xref = out.size();
    writeXref(out, offsets, xref);
    return out.toByteArray();
  }

  private static List<List<String>> paginate(List<String> lines) {
    final List<List<String>> pages = new ArrayList<>();
    for (int from = 0; from < lines.size(); from += LINES_PER_PAGE) {
      pages.add(lines.subList(from, Math.min(lines.size(), from + LINES_PER_PAGE)));
    }
    if (pages.isEmpty()) {
      pages.add(List.of());
    }
    return pages;
  }

  private static String pagesObject(int count) {
    final StringBuilder kids = new StringBuilder();
    for (int index = 0; index < count; index++) {
      kids.append(FIRST_PAGE_OBJECT + 2 * index).append(" 0 R ");
    }
    return "<< /Type /Pages /Kids [" + kids.toString().strip() + "] /Count " + count + " >>";
  }

  private static byte[] pageText(List<String> lines) {
    final ByteArrayOutputStream text = new ByteArrayOutputStream();
    writeAscii(text, "BT /F1 11 Tf 14 TL 50 800 Td\n");
    for (final String line : lines) {
      writeAscii(text, "(");
      text.writeBytes(encode(escape(line)));
      writeAscii(text, ") Tj T*\n");
    }
    writeAscii(text, "ET");
    return text.toByteArray();
  }

  private static String escape(String line) {
    return line.replace(' ', ' ')
        .replace(' ', ' ')
        .replaceAll("[\\r\\n\\t]", " ")
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)");
  }

  private static byte[] encode(String text) {
    final CharsetEncoder encoder =
        WIN_ANSI
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(new byte[] {'?'});
    try {
      final ByteBuffer encoded = encoder.encode(CharBuffer.wrap(text));
      final byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return bytes;
    } catch (CharacterCodingException e) {
      throw new IllegalStateException("Could not encode invoice text", e);
    }
  }

  private static void writeObject(ByteArrayOutputStream out, List<Integer> offsets, String body) {
    offsets.add(out.size());
    writeAscii(out, offsets.size() + " 0 obj\n" + body + "\nendobj\n");
  }

  private static void writeStream(ByteArrayOutputStream out, List<Integer> offsets, byte[] data) {
    offsets.add(out.size());
    writeAscii(out, offsets.size() + " 0 obj\n<< /Length " + data.length + " >>\nstream\n");
    out.writeBytes(data);
    writeAscii(out, "\nendstream\nendobj\n");
  }

  private static void writeXref(ByteArrayOutputStream out, List<Integer> offsets, int xref) {
    final StringBuilder table = new StringBuilder(128 + offsets.size() * 20);
    table.append("xref\n0 ").append(offsets.size() + 1).append("\n0000000000 65535 f \n");
    for (final int offset : offsets) {
      table.append(String.format(Locale.ROOT, "%010d 00000 n ", offset)).append('\n');
    }
    table
        .append("trailer\n<< /Size ")
        .append(offsets.size() + 1)
        .append(" /Root 1 0 R >>\nstartxref\n")
        .append(xref)
        .append("\n%%EOF\n");
    writeAscii(out, table.toString());
  }

  private static void writeAscii(ByteArrayOutputStream out, String text) {
    out.writeBytes(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  }
}
