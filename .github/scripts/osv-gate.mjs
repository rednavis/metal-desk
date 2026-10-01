// Turns an OSV-Scanner JSON report into a pass/fail decision, so the severity threshold is written
// down in code rather than left to the scanner's default (which fails on every advisory).
//
// Usage (from the repository root):
//   node .github/scripts/osv-gate.mjs <osv-report.json> [osv-scanner.toml]
//
// Policy:
//   - an advisory with CVSS >= 7.0 (high or critical) fails the job;
//   - lower, or unscored, advisories are listed but never block;
//   - every suppression in osv-scanner.toml needs a reason AND an expiry date (`ignoreUntil`), at
//     most MAX_SUPPRESSION_DAYS ahead. OSV-Scanner itself stops honouring an entry after that date,
//     so the advisory comes back; this script rejects an entry that lacks either field.
// Zero dependencies: Node's standard library only.
import { appendFileSync, existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

export const BLOCKING_SCORE = 7.0;
export const MAX_SUPPRESSION_DAYS = 90;

/** One row per advisory group: package, version, ids, score (NaN when unscored), source file. */
export function findings(report) {
  const rows = [];
  for (const result of report.results ?? []) {
    for (const pkg of result.packages ?? []) {
      for (const group of pkg.groups ?? []) {
        rows.push({
          name: pkg.package.name,
          version: pkg.package.version,
          ids: group.ids,
          score: Number.parseFloat(group.max_severity ?? ""),
          source: result.source?.path ?? "",
        });
      }
    }
  }
  return rows;
}

/** Splits findings into those that block the build and those that are only reported. */
export function classify(rows) {
  return {
    blocking: rows.filter((r) => r.score >= BLOCKING_SCORE),
    reported: rows.filter((r) => !(r.score >= BLOCKING_SCORE)),
  };
}

/** Problems with the `[[IgnoredVulns]]` entries of an osv-scanner.toml; empty when all are valid. */
export function suppressionProblems(toml, today = new Date()) {
  const problems = [];
  const entries = toml.split(/^\[\[IgnoredVulns\]\]\s*$/m).slice(1);
  for (const entry of entries) {
    const field = (key) => new RegExp(`^${key}\\s*=\\s*"([^"]*)"`, "m").exec(entry)?.[1]?.trim();
    const id = field("id");
    const label = id ?? "(entry without an id)";
    if (!id) problems.push(`${label}: missing id`);
    if (!field("reason")) problems.push(`${label}: missing reason`);
    const until = /^ignoreUntil\s*=\s*(\d{4}-\d{2}-\d{2})/m.exec(entry)?.[1];
    if (!until) {
      problems.push(`${label}: missing ignoreUntil (an expiry date, YYYY-MM-DD)`);
      continue;
    }
    const days = (Date.parse(until) - today.getTime()) / 86_400_000;
    if (Number.isNaN(days)) problems.push(`${label}: ignoreUntil ${until} is not a date`);
    else if (days > MAX_SUPPRESSION_DAYS)
      problems.push(`${label}: ignoreUntil ${until} is more than ${MAX_SUPPRESSION_DAYS} days away`);
  }
  return problems;
}

const line = (r) =>
  `${r.name} ${r.version}  ${r.ids.join(", ")}  CVSS ${Number.isNaN(r.score) ? "unscored" : r.score}`;

function main([reportPath, configPath = "osv-scanner.toml"]) {
  if (!reportPath) throw new Error("usage: osv-gate.mjs <osv-report.json> [osv-scanner.toml]");
  const problems = existsSync(configPath)
    ? suppressionProblems(readFileSync(configPath, "utf8"))
    : [];
  const { blocking, reported } = classify(findings(JSON.parse(readFileSync(reportPath, "utf8"))));

  const out = [
    `Blocking (CVSS >= ${BLOCKING_SCORE}): ${blocking.length}`,
    ...blocking.map((r) => `  ${line(r)}`),
    `Reported, not blocking: ${reported.length}`,
    ...reported.map((r) => `  ${line(r)}`),
    ...problems.map((p) => `Invalid suppression: ${p}`),
  ];
  console.log(out.join("\n"));
  if (process.env.GITHUB_STEP_SUMMARY)
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, "```\n" + out.join("\n") + "\n```\n");
  return blocking.length === 0 && problems.length === 0 ? 0 : 1;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) process.exitCode = main(process.argv.slice(2));
