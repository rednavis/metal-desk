// Run from the repository root: node --test .github/scripts/
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { affected, loadGraph, parseFilters } from "./affected.mjs";

const graph = loadGraph();
const filters = parseFilters(readFileSync(".github/path-filters.yml", "utf8"));
const ALL = graph.modules;
const run = (...files) => affected(files, graph, filters);

test("the graph is derived from the build files and covers all seven JVM modules", () => {
  assert.equal(ALL.length, 7);
  assert.deepEqual(graph.deps.get(":libs:share"), []);
  assert.deepEqual(graph.deps.get(":services:pricing-bridge"), [":libs:share"]);
});

test("libs/share marks every module affected", () => {
  assert.deepEqual(run("libs/share/src/main/java/X.java").modules, ALL);
});

test("libs/payments marks itself and services:api only", () => {
  assert.deepEqual(run("libs/payments/build.gradle.kts").modules, [
    ":libs:payments",
    ":services:api",
  ]);
});

test("libs/persistence marks itself, services:api and apps:admin", () => {
  assert.deepEqual(run("libs/persistence/src/X.java").modules, [
    ":libs:persistence",
    ":services:api",
    ":apps:admin",
  ]);
});

test("apps/admin marks services:api, whose tests run the admin application", () => {
  assert.deepEqual(run("apps/admin/src/X.java").modules, [":services:api", ":apps:admin"]);
});

test("a leaf module marks only itself", () => {
  assert.deepEqual(run("services/pricing-bridge/src/X.java").modules, [":services:pricing-bridge"]);
});

test("apps/web alone marks the frontend and no JVM module", () => {
  const r = run("apps/web/src/main.tsx");
  assert.deepEqual(r.modules, []);
  assert.equal(r.frontend, true);
});

for (const file of [
  "gradle/libs.versions.toml",
  "build-logic/src/X.kts",
  "config/checkstyle/checkstyle.xml",
]) {
  test(`${file} marks every JVM module`, () => assert.deepEqual(run(file).modules, ALL));
}

for (const file of ["package.json", "eslint.config.mjs", "pnpm-lock.yaml"]) {
  test(`${file} marks the frontend and no JVM module`, () => {
    const r = run(file);
    assert.deepEqual(r.modules, []);
    assert.equal(r.frontend, true);
  });
}

test("a docs-only change marks neither build", () => {
  const r = run("docs/architecture.md");
  assert.deepEqual(r.modules, []);
  assert.equal(r.frontend, false);
  assert.equal(r.docs, true);
});

test("a Java DTO the frontend contract tests read also marks the frontend", () => {
  assert.equal(run("services/api/src/main/java/com/X.java").frontend, true);
  assert.equal(run("services/api/src/test/java/com/X.java").frontend, false);
});

test("a push to master (no file list) marks everything", () => {
  assert.deepEqual(affected(null, graph, filters), { modules: ALL, frontend: true, docs: true });
});

test("a workflow change marks everything JVM and the frontend", () => {
  const r = run(".github/workflows/ci.yml");
  assert.deepEqual(r.modules, ALL);
  assert.equal(r.frontend, true);
});

test("an unsupported glob in path-filters.yml is rejected", () => {
  assert.throws(() => parseFilters('x:\n  - "a/*.kts"\n'), /unsupported glob/);
});
