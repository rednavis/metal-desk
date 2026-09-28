/**
 * Transactional mail (ADR-0002, Modernization Plan Phase 2): the {@link
 * com.rednavis.metaldesk.mail.MailSender} interface, the {@link
 * com.rednavis.metaldesk.mail.TransactionalMail} it sends, and the {@link
 * com.rednavis.metaldesk.mail.MailRenderer} that turns one of the ten {@link
 * com.rednavis.metaldesk.mail.MailTemplate}s into a localized subject and body.
 *
 * <p>The template set is closed, one template for each notification the BRD requires, with the
 * customer and staff variants kept separate. Every template exists in English, the default, and
 * German. The locale is always an explicit parameter and nothing reads a default; a missing
 * translation falls back to the default and is logged, and never fails an order.
 *
 * <p>There is no mail-server configuration here at all: a mail provider has no HTTP boundary to
 * intercept, so the fake is the interface's own in-process implementation, and a real sender
 * arrives with deployment.
 */
package com.rednavis.metaldesk.mail;
