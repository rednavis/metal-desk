# T-020 — `libs/mail`: the transactional mail SPI, in-process fake and localized templates

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#21](https://github.com/rednavis/metal-desk/issues/21)

**Milestone:** M1 Domain model and libs · **Estimate:** 2 h

**Preconditions** — `T-010` and `T-011` merged (`EmailAddress`, the exception taxonomy). `T-014` merged if
the order-confirmation template is implemented in this task rather than stubbed.

**Goal** — Give `libs/mail` a transactional mail abstraction with an in-process fake sender for tests and
local dev, plus the localized template set every notification requirement in the BRD needs.

## 1. Why this task exists

The [Modernization Plan, Phase 2](../docs/modernization-plan.md) asks for "a fake in-process sender for
tests/dev", and [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) explains why it is a fake
rather than WireMock: there is no HTTP boundary to intercept for a mail provider chosen later, so the
substitution point is the interface itself.

The fake is not a test detail — it is what makes the whole system runnable locally with no account, and it is
what lets `T-041`'s end-to-end test assert that a confirmation email was actually produced rather than
hoping it was.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Transactional mail abstraction with an in-process fake | [Modernization Plan, Phase 2](../docs/modernization-plan.md), parent issue #2 |
| Mock every external dependency at the boundary | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |
| Email verification code on registration | [FR-2.3](../docs/business-requirements.md#72-authentication), [FR-2.6](../docs/business-requirements.md#72-authentication) |
| Time-limited password-reset link | [FR-2.4](../docs/business-requirements.md#72-authentication) |
| Invoice emailed to customer **and** staff, in the customer's locale | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Confirmation notifies customer **and** staff | [FR-8.1](../docs/business-requirements.md#78-checkout--step-5-confirmation) |
| Handoff confirms receipt with a reference number | [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Inquiry notifications to both parties | [FR-9.1](../docs/business-requirements.md#79-manager-mediated-inquiries), [FR-9.2](../docs/business-requirements.md#79-manager-mediated-inquiries) |
| UI language switch re-renders transactional content | [FR-1.7](../docs/business-requirements.md#71-home--market-data) |

## 3. Deliverables

Under `libs/mail/src/main/java/com/rednavis/metaldesk/mail/`:

| Path | What |
|---|---|
| `MailSender.java` | The SPI: `Mono<Void> send(TransactionalMail)` |
| `TransactionalMail.java` | Recipients, subject, rendered body, locale, attachments, template id |
| `MailAttachment.java` | Filename, content type, bytes — carries the `T-019` invoice PDF |
| `MailTemplate.java` | The enumerated template set — see §4 |
| `MailRenderer.java` | Template id + model + locale → rendered subject and body |
| `MailException.java` | Extends `DomainException`; transport failure |
| `fake/InProcessMailSender.java` | Records sent mail in memory, exposes it for assertions |
| `fake/RecordedMail.java` | One recorded send |
| `resources/mail/<template>_<locale>.*` | The template files |

Under `libs/mail/src/test/...`: `InProcessMailSenderTest.java`, `MailRendererTest.java`.

## 4. Specification

**The template set is closed and enumerated.** `MailTemplate` names exactly the notifications the BRD
requires, each with the FR it serves in its Javadoc:

| Template | Requirement |
|---|---|
| `EMAIL_VERIFICATION` | FR-2.3, FR-2.6 |
| `PASSWORD_RESET` | FR-2.4 |
| `ORDER_CONFIRMATION_CUSTOMER` | FR-8.1 |
| `ORDER_NOTIFICATION_STAFF` | FR-8.1 |
| `INVOICE_CUSTOMER` | FR-6.2 |
| `INVOICE_STAFF` | FR-6.2 |
| `HANDOFF_RECEIPT_CUSTOMER` | FR-5.3 |
| `HANDOFF_NOTIFICATION_STAFF` | FR-5.3 |
| `INQUIRY_RECEIPT_CUSTOMER` | FR-9.1, FR-9.2 |
| `INQUIRY_NOTIFICATION_STAFF` | FR-9.1, FR-9.2 |

Customer and staff variants are separate templates, not one template with a flag: FR-8.1 and FR-6.2 both
require notifying both parties, and the two messages carry different information.

**Locale is an explicit parameter everywhere.** `MailRenderer.render(MailTemplate, Map<String,Object> model,
Locale)`. No `Locale.getDefault()`, no thread-local, no ambient default — the same rule as `T-019`, for the
same reason. A missing template for a requested locale falls back to a documented default locale and logs at
warn with `@Slf4j`; it must not throw, because a missing translation should not fail an order.

**`InProcessMailSender` is a queryable recorder, not a no-op.** It stores every `TransactionalMail` and
exposes `List<RecordedMail> sent()`, `sentTo(EmailAddress)`, `sentOf(MailTemplate)` and `clear()`. Make it
thread-safe — `T-041`'s end-to-end test will drive it from a reactive pipeline. Its Javadoc must state it is
for tests and local dev only.

**`MailSender` is reactive and returns `Mono<Void>`**, per Architecture §5. Sending must never block a
request thread.

**No credentials, no hostnames, no real addresses.** Templates and tests use obviously synthetic addresses on
a reserved example domain. There is no SMTP configuration in this task at all — a real sender arrives with
deployment (`T-075`), and its absence is the point.

**Templates render both a subject and a body, and the subject is localized too.** A localized body under an
English subject is the most common half-done version of this.

## 5. Acceptance criteria

1. `./gradlew :libs:mail:build` is `BUILD SUCCESSFUL`.
2. `MailTemplate` declares exactly the ten constants in §4, each with its FR cited in Javadoc.
3. A template file exists for every `MailTemplate` in the default locale — asserted by a test that iterates
   the enum, so adding a constant without a template fails the build.
4. `render` with a locale that has no template returns the default-locale rendering and does not throw.
5. Rendering the same template in two locales produces different subjects **and** different bodies.
6. `InProcessMailSender` records a send and `sentTo` / `sentOf` retrieve it; `clear()` empties it.
7. Concurrent sends from multiple threads all appear in `sent()` — asserted with a small concurrency test.
8. `MailSender.send` returns a reactive type; no `.block()` anywhere in `src/main`.
9. `grep` finds no SMTP host, credential or non-example email domain under `libs/mail/`.
10. `libs/mail/build.gradle.kts` contains no version literal.

## 6. Verification

```
cd <repo>
./gradlew :libs:mail:build :libs:mail:test
grep -rn 'Locale.getDefault\|ThreadLocal' libs/mail/src/main   # expect nothing
grep -rn '\.block()' libs/mail/src/main                        # expect nothing
grep -rniE 'smtp|password|api[_-]?key' libs/mail/src           # expect nothing
ls libs/mail/src/main/resources/mail | wc -l                   # expect >= 10
```

Expected: `BUILD SUCCESSFUL`; no ambient locale; no blocking; no mail credentials; at least one template per
enum constant.

## 7. Out of scope

A real SMTP or provider-API sender, and its configuration (`T-075`). Wiring templates into the flows that
trigger them (`T-033`, `T-038`). Rendering the invoice PDF (`T-019`) — this task only attaches it. Email
deliverability, bounce handling, unsubscribe. HTML email design.

## 8. Hazards

- **One template with a customer/staff flag** reads as simpler and then forces both audiences to share a
  subject line and a body. FR-8.1 and FR-6.2 both need genuinely different content.
- `Locale.getDefault()` makes every email render in the server's locale in Cloud Run while passing locally.
- Throwing on a missing translation turns a cosmetic gap into a failed checkout. Fall back and log.
- A non-thread-safe recorder drops sends under a reactive pipeline and produces a flaky `T-041`.
- Putting SMTP configuration in this task adds a credential-shaped thing to the repo for no requirement in
  this phase.
- Using a real-looking email domain in a fixture risks sending mail somewhere if a real sender is ever wired
  against the same fixtures. Use a reserved example domain.

## 9. On completion

Mark the T-020 row done in [`README.md`](README.md). Record the default locale and the template file format
in its Notes column — `T-033` and `T-038` render against them, and `T-041` asserts on them.
