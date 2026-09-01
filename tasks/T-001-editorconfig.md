# T-001 — EditorConfig for the whole tree

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with [`CONTRIBUTING.md`](../CONTRIBUTING.md#code-style)
> or an [ADR](../docs/adr/), that document wins** — open an issue rather than implementing either
> version. Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#6 — Add EditorConfig for consistent formatting](https://github.com/rednavis/metal-desk/issues/6)

**This task:** [#9](https://github.com/rednavis/metal-desk/issues/9)

**Milestone:** M0 Repo hygiene · **Estimate:** 30 min

**Preconditions** — none. Authorable today, on a clean checkout, with no build tooling installed.

**Goal** — Add a single root `.editorconfig` whose rules agree, byte for byte, with what Spotless
(`googleJavaFormat()`) and Prettier already produce, so an editor never fights the build.

## 1. Why this task exists

The repository already has two authoritative formatters — Spotless for Java and Prettier for
TypeScript — but neither runs while you type. Without `.editorconfig` an editor silently applies its
own defaults (4-space Java indent, CRLF on Windows, no final newline), and the first `./gradlew build`
after that rewrites every line the editor touched. The diff then hides the real change.

This is the [tooling-drift pattern](../docs/lessons-learned.md#configuration-and-tooling-drift-compounds-silently)
in miniature, and it costs nothing to close now.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Java formatting is Google Java Format: 2-space indent, 100-col limit | `config/checkstyle/checkstyle.xml` (`Indentation`, `LineLength`) and `build-logic/src/main/kotlin/metaldesk.quality-conventions.gradle.kts` |
| TS/TSX/JSON/YAML formatting is Prettier: 2-space indent, 100-col | `.prettierrc.json` |
| Prettier does **not** own `*.md` or `docs/` | `.prettierignore` |
| Formatting tools are Spotless + Prettier, nothing else | [`CONTRIBUTING.md`](../CONTRIBUTING.md#code-style) |

**Precedence:** where this task and a formatter config disagree, **the formatter config wins** — it is
what CI and `./gradlew build` actually enforce. Report the mismatch instead of picking a side.

## 3. Deliverables

| Path | What |
|---|---|
| `.editorconfig` | The only file this task creates |

## 4. Specification

One root file, `root = true`. A `[*]` section carrying `charset = utf-8`, `end_of_line = lf`,
`insert_final_newline = true`, `trim_trailing_whitespace = true`, `indent_style = space`,
`indent_size = 2`.

Then the per-language overrides that differ from that baseline, and **only** those:

| Selector | Settings | Why |
|---|---|---|
| `[*.java]` | `max_line_length = 100` | Matches `LineLength` in `config/checkstyle/checkstyle.xml` and google-java-format's own limit |
| `[*.{ts,tsx,js,mjs,cjs,json,jsonc}]` | `max_line_length = 100` | Matches `printWidth` in `.prettierrc.json` |
| `[*.{kt,kts}]` | `indent_size = 4` | The existing `build-logic/**/*.gradle.kts` and every `build.gradle.kts` are already 4-space — verify with `grep` before writing this, and drop the override if they are not |
| `[*.md]` | `trim_trailing_whitespace = false` | Two trailing spaces are a hard line break in Markdown; stripping them silently reflows `docs/` |
| `[*.{yml,yaml}]` | `indent_size = 2` | Explicit, because a YAML file broken by a 4-space editor default fails CI in a way that reads as a workflow bug |
| `[Makefile]`, `[*.tf]` | omit | No Makefile exists, and Terraform arrives in M5 (`T-070`) — do not pre-declare rules for files nobody has written |

Do not set `indent_size` for `*.java` — the `[*]` baseline of 2 already covers it, and a second
declaration is one more place to drift.

## 5. Acceptance criteria

1. `.editorconfig` exists at the repository root and starts with `root = true`.
2. Every `indent_size` for a Java or TypeScript selector is `2`, and every `max_line_length` is `100`.
3. `[*.md]` sets `trim_trailing_whitespace = false`.
4. `./gradlew spotlessCheck` and `pnpm run format:check` both pass with **no file modified** — proving
   the EditorConfig rules and the formatters agree rather than merely coexist.
5. `git diff --stat` after the two checks above is empty apart from `.editorconfig` itself.
6. No file other than `.editorconfig` is added or modified.

## 6. Verification

```
cd <repo>
grep -c indent_size .editorconfig
./gradlew spotlessCheck && pnpm install --frozen-lockfile && pnpm run format:check
git status --short          # expect only: A/?? .editorconfig
grep -rn 'indent_size' .editorconfig
```

Expected: both checks `BUILD SUCCESSFUL` / "All matched files use Prettier code style!", and a clean
tree apart from the new file.

## 7. Out of scope

Wiring a formatter into a git hook or into CI (`T-060`…`T-065`). Changing `.prettierrc.json`,
`.prettierignore` or any Checkstyle rule — this task adapts to them, never the reverse. Reformatting
any existing file: if `.editorconfig` would change an existing file, the rule is wrong, not the file.

## 8. Hazards

- **Do not add `indent_size = 4` for Java** because a Sun-style Checkstyle fragment was merged into
  `config/checkstyle/checkstyle.xml`. The effective `Indentation` rule is 2 — check it before trusting
  any 4-space claim.
- An `[*]` block with `trim_trailing_whitespace = true` will, in most editors, silently strip the
  meaningful trailing spaces in `docs/`. The `[*.md]` override is not optional.
- Some editors apply `max_line_length` as a hard wrap. That is an editor setting, not an EditorConfig
  guarantee; do not compensate by lowering the value.

## 9. On completion

Mark the T-001 row done in [`README.md`](README.md) and record any deviation in its Notes column — in
particular if `build.gradle.kts` files turned out **not** to be 4-space indented, since that changes
the `[*.{kt,kts}]` row above.
