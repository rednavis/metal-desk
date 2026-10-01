// Decides which parts of the repository a change affects, so CI can build only those.
//
// Usage (from the repository root):
//   node .github/scripts/affected.mjs <base-sha> [head-ref]    pull request: diff against the base
//   node .github/scripts/affected.mjs --all                   push to the default branch: everything
//
// With GITHUB_OUTPUT set, results are appended to it as job outputs; otherwise they are printed.
// Zero dependencies: Node's standard library only.
import { execFileSync } from "node:child_process";
import { appendFileSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");

/** Output name for a Gradle project path: `:libs:share` -> `libs-share`. */
export const outputName = (project) => project.slice(1).replaceAll(":", "-");

const projectDir = (project) => project.slice(1).replaceAll(":", "/");

/** Module project paths from `include("a:b", ...)` in settings.gradle.kts. */
export function parseModules(settings) {
  const block = /\binclude\(([^)]*)\)/s.exec(settings);
  if (!block) throw new Error("no include(...) block found in settings.gradle.kts");
  return [...block[1].matchAll(/"([^"]+)"/g)].map((m) => `:${m[1]}`);
}

/** Every `project(":x:y")` a build file mentions, whatever the configuration it is declared in. */
export function parseProjectDeps(buildFile) {
  return [...buildFile.matchAll(/\bproject\("(:[^"]+)"\)/g)].map((m) => m[1]);
}

/**
 * Reverse-dependency closure: for each module, itself plus every module that depends on it,
 * directly or transitively. `deps` maps a module to the modules it depends on.
 */
export function affectedBy(modules, deps) {
  const closure = new Map(modules.map((m) => [m, new Set([m])]));
  let grew = true;
  while (grew) {
    grew = false;
    for (const m of modules) {
      for (const dep of deps.get(m) ?? []) {
        for (const hit of closure.get(m)) {
          if (!closure.get(dep).has(hit)) {
            closure.get(dep).add(hit);
            grew = true;
          }
        }
      }
    }
  }
  return closure;
}

/** Reads the module graph from the build files — the single derivation the CI table relies on. */
export function loadGraph(root = REPO_ROOT) {
  const modules = parseModules(readFileSync(join(root, "settings.gradle.kts"), "utf8"));
  const deps = new Map();
  for (const m of modules) {
    const file = join(root, projectDir(m), "build.gradle.kts");
    const found = parseProjectDeps(readFileSync(file, "utf8"));
    for (const d of found) {
      if (!modules.includes(d)) {
        throw new Error(`${m} depends on ${d}, which is not an included module`);
      }
    }
    deps.set(m, [...new Set(found)]);
  }
  return { modules, deps };
}

/** Parses the tiny YAML subset path-filters.yml uses: `key:` followed by `  - "glob"` lines. */
export function parseFilters(text) {
  const filters = {};
  let key = null;
  for (const raw of text.split("\n")) {
    const line = raw.trimEnd();
    if (line === "" || line.startsWith("#")) continue;
    const head = /^([a-z-]+):$/.exec(line);
    const item = /^ {2}- "([^"]+)"$/.exec(line);
    if (head) {
      key = head[1];
      filters[key] = [];
    } else if (item && key) {
      if (/[*?[{]/.test(item[1].replace(/\/\*\*$/, ""))) {
        throw new Error(`unsupported glob "${item[1]}": only exact paths and "dir/**" are allowed`);
      }
      filters[key].push(item[1]);
    } else {
      throw new Error(`cannot parse path-filters.yml line: ${raw}`);
    }
  }
  return filters;
}

export const matches = (file, globs) =>
  globs.some((g) => (g.endsWith("/**") ? file.startsWith(g.slice(0, -2)) : file === g));

/**
 * @param {string[]|null} files changed paths, or null to mean "everything" (push to master)
 * @returns {{modules: string[], frontend: boolean, docs: boolean}}
 */
export function affected(files, { modules, deps }, filters) {
  if (files === null) return { modules: [...modules], frontend: true, docs: true };
  const frontend = files.some((f) =>
    matches(f, [...filters.frontend, ...filters["frontend-contract"]]),
  );
  const docs = files.some((f) => matches(f, filters.docs));
  if (files.some((f) => matches(f, filters["jvm-shared"]))) {
    return { modules: [...modules], frontend, docs };
  }
  const closure = affectedBy(modules, deps);
  const hit = new Set();
  for (const m of modules) {
    if (files.some((f) => matches(f, [`${projectDir(m)}/**`]))) {
      closure.get(m).forEach((x) => hit.add(x));
    }
  }
  return { modules: modules.filter((m) => hit.has(m)), frontend, docs };
}

function main(argv) {
  const graph = loadGraph();
  const filters = parseFilters(readFileSync(join(REPO_ROOT, ".github/path-filters.yml"), "utf8"));
  let files = null;
  if (argv[0] !== "--all") {
    if (!argv[0]) throw new Error("usage: affected.mjs <base-sha> [head-ref] | --all");
    const diff = execFileSync("git", ["diff", "--name-only", argv[0], argv[1] ?? "HEAD"], {
      cwd: REPO_ROOT,
      encoding: "utf8",
    });
    files = diff.split("\n").filter(Boolean);
  }
  const result = affected(files, graph, filters);
  const lines = [
    ...graph.modules.map((m) => `${outputName(m)}=${result.modules.includes(m)}`),
    `jvm=${result.modules.length > 0}`,
    `jvm-modules=${JSON.stringify(result.modules)}`,
    `frontend=${result.frontend}`,
    `docs=${result.docs}`,
  ];
  console.log(lines.join("\n"));
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `${lines.join("\n")}\n`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main(process.argv.slice(2));
