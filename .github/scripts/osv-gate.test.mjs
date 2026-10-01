// Run from the repository root: node --test .github/scripts/
import assert from "node:assert/strict";
import { test } from "node:test";
import { classify, findings, suppressionProblems } from "./osv-gate.mjs";

const report = (...groups) => ({
  results: [
    {
      source: { path: "bom.json" },
      packages: groups.map(([name, ids, max_severity]) => ({
        package: { name, version: "1.0.0" },
        groups: [{ ids, max_severity }],
      })),
    },
  ],
});

test("high and critical block; medium, low and unscored are only reported", () => {
  const { blocking, reported } = classify(
    findings(
      report(
        ["critical", ["GHSA-1"], "9.8"],
        ["high", ["GHSA-2"], "7.0"],
        ["medium", ["GHSA-3"], "6.9"],
        ["low", ["GHSA-4"], "2.1"],
        ["unscored", ["GHSA-5"], ""],
      ),
    ),
  );
  assert.deepEqual(
    blocking.map((r) => r.name),
    ["critical", "high"],
  );
  assert.deepEqual(
    reported.map((r) => r.name),
    ["medium", "low", "unscored"],
  );
});

test("an empty report passes", () => {
  assert.deepEqual(classify(findings({})).blocking, []);
});

const today = new Date("2026-10-01");
const entry = (body) => `[[IgnoredVulns]]\n${body}\n`;

test("a suppression with a reason and a near expiry is valid", () => {
  const toml = entry('id = "GHSA-1"\nignoreUntil = 2026-11-01\nreason = "no fix released"');
  assert.deepEqual(suppressionProblems(toml, today), []);
});

test("a suppression without a reason or without an expiry is rejected", () => {
  assert.match(
    suppressionProblems(entry('id = "GHSA-1"\nignoreUntil = 2026-11-01'), today)[0],
    /reason/,
  );
  assert.match(suppressionProblems(entry('id = "GHSA-1"\nreason = "x"'), today)[0], /ignoreUntil/);
});

test("a suppression that outlives the maximum is rejected", () => {
  const toml = entry('id = "GHSA-1"\nignoreUntil = 2027-10-01\nreason = "x"');
  assert.match(suppressionProblems(toml, today)[0], /days away/);
});

test("a file with no suppressions has no problems", () => {
  assert.deepEqual(suppressionProblems("# nothing ignored\n", today), []);
});
