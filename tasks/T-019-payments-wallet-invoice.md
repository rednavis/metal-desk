# T-019 — `libs/payments`: the wallet and invoice providers

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#20](https://github.com/rednavis/metal-desk/issues/20)

**Milestone:** M1 Domain model and libs · **Estimate:** 2 h

**Preconditions** — `T-017` and `T-018` merged. `T-012` merged, for `TaxCategory`.

**Goal** — Complete the [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction)
trio: `WalletProvider` (account-based, WireMock-backed) and `InvoiceProvider` (PDF generation, **no live
gateway call**), including the two-document split
[FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method) requires.

## 1. Why this task exists

Architecture §4 lists three provider families, and the third one is structurally different: `InvoiceProvider`
makes no outbound payment call at all. It generates a document and emails it. If the SPI from `T-017` only
fits the two that call a gateway, the abstraction is wrong — and this task is where that shows up.

FR-6.2 is also the only requirement in the system with a document-count rule:

> **Invoice** is a first-class payment method, not a fallback: the system generates a PDF invoice, capped to
> two documents per order when the cart mixes tax-exempt and taxable product categories (§8, BR-3), and
> emails it to the customer and a copy to staff, in the customer's selected locale.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Three provider families; invoice makes no live gateway call | [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) |
| Invoice is first-class, not a fallback | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Two documents max, when the cart mixes tax treatments | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method), [BR-3](../docs/business-requirements.md#8-business-rules), [BR-4](../docs/business-requirements.md#8-business-rules) |
| Emailed to the customer **and** staff, in the customer's locale | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Gateway methods restricted above a value ceiling — invoice is the alternative | [BR-9](../docs/business-requirements.md#8-business-rules) |
| Wallet is a separate account-based provider | [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction), [FR-6.1](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Everything external is mocked | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |

## 3. Deliverables

Under `libs/payments/src/main/java/com/rednavis/metaldesk/payments/`:

| Path | What |
|---|---|
| `wallet/WalletProvider.java` | `PaymentProvider` for the account-based wallet |
| `wallet/WalletClient.java`, `wallet/WalletConfiguration.java` | Non-blocking client, externally configured base URL |
| `invoice/InvoiceProvider.java` | `PaymentProvider` returning `DocumentIssued`, never calling a gateway |
| `invoice/InvoiceDocument.java` | The rendered document: bytes, filename, `TaxCategory` scope, locale |
| `invoice/InvoiceSplitter.java` | The pure BR-3/FR-6.2 rule: order lines → one or two document scopes |
| `invoice/InvoiceRenderer.java` | The PDF rendering port |
| `invoice/InvoiceNumber.java` | The document identifier used as the `ProviderReference` |

Under `libs/payments/src/test/...` and `src/test/resources/wiremock/wallet/`:

| Path | What |
|---|---|
| `WalletProviderTest.java` | Success, insufficient-balance decline, timeout |
| `InvoiceSplitterTest.java` | One document, two documents, and the never-three assertion |
| `InvoiceProviderTest.java` | Asserts **no** HTTP call is made |
| `mappings/`, `__files/` | Wallet stubs only — the invoice provider has nothing to stub |

## 4. Specification

**`WalletProvider` mirrors `T-018`'s shape.** Same three stubbed paths, same distinction between `Declined`
and `PaymentProviderException`, same prohibition on hostnames and credentials in `src/main`. Its decline
taxonomy adds the account-based case — insufficient balance maps to `DeclineReason.INSUFFICIENT_FUNDS`.
Reuse `T-018`'s patterns rather than inventing a second client style.

**`InvoiceProvider` makes no outbound call, and a test proves it.** `authorise` renders the document(s),
returns `PaymentOutcome.DocumentIssued` with the `InvoiceNumber` as the `ProviderReference`, and the
resulting `PaymentStatus` is pending — an invoice is a promise to pay, not a capture. Assert the absence of
HTTP by giving the test a WireMock server with **zero** stubs and verifying it received no request; a
provider that quietly calls something will fail with an unmatched-request error.

**`InvoiceSplitter` is the FR-6.2 rule, and it is pure.** Signature shape:
`List<DocumentScope> split(List<OrderLine> lines)`. Group the lines by the snapshotted `TaxCategory` on each
line (`T-014` put it there for exactly this reason — do **not** resolve it live from the product).

- All lines share one tax category → **one** scope.
- Lines span both categories → **two** scopes, one per category.
- Never three, whatever the input. Assert it: `split(...).size() <= 2` for every fixture, including an
  empty list and a single line.

The cap is two because `TaxCategory` has two members (BR-4). Document that dependency explicitly — if a
third tax category is ever added, this rule and FR-6.2 both need revisiting, and the Javadoc should say so.

**`InvoiceRenderer` is a port, not a PDF library call.** Declare the interface here and implement it with
whatever the catalog provides; if a PDF library must be added, add the alias to
`gradle/libs.versions.toml`, never a version in the build file. For this phase an implementation that
produces a valid, minimal PDF with the order's totals is sufficient — layout polish is not in scope.

**Locale is an input, not a lookup.** FR-6.2 renders in the customer's selected locale. `InvoiceProvider`
takes the locale from the `PaymentIntent` or an explicit parameter; it must not read a thread-local or a
static default. Emailing is **not** this task — `libs/mail` (`T-020`) sends, and `T-038` wires them.

**BR-9's relevance.** BR-9 restricts gateway methods above a value ceiling, which makes invoice the path for
high-value orders. Nothing to implement here; note it in `InvoiceProvider`'s Javadoc so the significance of
this provider is not mistaken for a legacy fallback.

## 5. Acceptance criteria

1. `./gradlew :libs:payments:build` is `BUILD SUCCESSFUL`.
2. `WalletProvider.supports()` is true only for the wallet method; `InvoiceProvider.supports()` only for
   invoice; neither overlaps `GatewayProvider`.
3. Wallet: success → `Captured`; insufficient balance → `Declined(INSUFFICIENT_FUNDS)`; delay stub →
   `PaymentProviderException`.
4. `InvoiceProvider.authorise` returns `DocumentIssued`, and the test's stubless WireMock server records
   **zero** requests.
5. The resulting invoice `PaymentStatus` is pending, not captured.
6. `InvoiceSplitter` returns one scope for a single-category order, two for a mixed order, and never more
   than two for any fixture including empty and single-line.
7. The splitter reads the `TaxCategory` snapshotted on the `OrderLine` and never dereferences a `Product` —
   asserted reflectively or by a mutation test as in `T-014` AC-3.
8. A rendered document is a non-empty byte array whose first bytes are a valid PDF header.
9. Two different locales produce two different rendered outputs for the same order.
10. No hostname or credential under `libs/payments/src/main`; no version literal in its build file.

## 6. Verification

```
cd <repo>
./gradlew :libs:payments:build :libs:payments:test
grep -rn 'Product' libs/payments/src/main/java/com/rednavis/metaldesk/payments/invoice/InvoiceSplitter.java   # expect nothing
grep -rniE 'api[_-]?key|bearer |https?://[a-z]' libs/payments/src/main   # expect nothing
grep -rn 'Locale.getDefault\|ThreadLocal' libs/payments/src/main   # expect nothing
ls libs/payments/src/test/resources/wiremock   # expect wallet stubs only, no invoice dir
```

Expected: `BUILD SUCCESSFUL`; no `Product` in the splitter; no hostnames or keys; no default-locale lookup;
no invoice stubs, because the invoice provider calls nothing.

## 7. Out of scope

Sending the emails (`T-020` provides the sender, `T-038` wires it). Choosing a provider at checkout and
applying BR-9's ceiling (`T-037`). Invoice layout, branding and typography. Storing rendered documents
(`T-030`). Credit-note or refund documents.

## 8. Hazards

- **An `InvoiceProvider` that makes any HTTP call** contradicts Architecture §4 and will be added
  accidentally — for a tax-rate lookup, or a template fetch. AC-4's stubless server is the guard.
- Resolving `TaxCategory` from the live `Product` inside the splitter reintroduces the BR-2 violation
  `T-014` closed, and the split would change for a historical order after a category edit.
- Returning `Captured` for an invoice marks an unpaid order as paid and lets it transition past
  `AWAITING_PAYMENT` in `T-015`'s machine.
- `Locale.getDefault()` produces documents in the server's locale, which passes every local test and is
  wrong in Cloud Run.
- Allowing three or more documents when the splitter is fed unexpected input violates FR-6.2's explicit cap;
  assert the bound rather than trusting the grouping.

## 9. On completion

Mark the T-019 row done in [`README.md`](README.md). Record which PDF mechanism was used and any catalog
alias added, and note in the Notes column that the two-document cap is tied to `TaxCategory` having exactly
two members.
