package com.rednavis.metaldesk.persistence.document;

/**
 * One stored invoice document. The content is Base64 text, so the record holds no array.
 *
 * @param filename the file name
 * @param scope the tax scope, by name
 * @param locale the language tag the document was rendered in
 * @param contentBase64 the PDF, Base64-encoded
 */
public record InvoiceFileDocument(
    String filename, String scope, String locale, String contentBase64) {}
