/**
 * Just enough Java parsing for the contract test: the component names of a record and the constants
 * of an enum, read from the source files in this repository. Not a Java parser; it handles the shapes
 * these DTOs actually use (annotations, generics, nested types, comments) and nothing else.
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const REPO_ROOT = resolve(import.meta.dirname, "../../../..");

export function readJava(relativePath: string): string {
  const source = readFileSync(resolve(REPO_ROOT, relativePath), "utf8");
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
}

/** Splits on commas that are not inside `()` or `<>`. */
function splitTopLevel(text: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = "";
  for (const char of text) {
    if (char === "(" || char === "<") depth++;
    if (char === ")" || char === ">") depth--;
    if (char === "," && depth === 0) {
      parts.push(current);
      current = "";
    } else {
      current += char;
    }
  }
  if (current.trim() !== "") parts.push(current);
  return parts;
}

/** The component names of the record called `name`, in declaration order. */
export function recordComponents(source: string, name: string): string[] {
  const header = new RegExp(`\\brecord\\s+${name}\\s*(?:<[^>(]*>)?\\s*\\(`).exec(source);
  if (!header) throw new Error(`record ${name} not found`);
  const start = header.index + header[0].length;
  let depth = 1;
  let end = start;
  while (depth > 0 && end < source.length) {
    if (source[end] === "(") depth++;
    if (source[end] === ")") depth--;
    end++;
  }
  return splitTopLevel(source.slice(start, end - 1))
    .map((component) => component.replace(/@\w+(\([^)]*\))?/g, "").trim())
    .filter((component) => component !== "")
    .map((component) => component.split(/\s+/).at(-1) ?? "");
}

/** The constants of the enum called `name`, in declaration order. */
export function enumConstants(source: string, name: string): string[] {
  const header = new RegExp(`\\benum\\s+${name}\\s*\\{`).exec(source);
  if (!header) throw new Error(`enum ${name} not found`);
  const rest = source.slice(header.index + header[0].length);
  let depth = 0;
  let end = 0;
  for (; end < rest.length; end++) {
    const char = rest[end];
    if (char === "(") depth++;
    if (char === ")") depth--;
    if (depth === 0 && (char === ";" || char === "}")) break;
  }
  return splitTopLevel(rest.slice(0, end))
    .map((constant) => /^\s*([A-Z][A-Z0-9_]*)/.exec(constant)?.[1])
    .filter((constant): constant is string => constant !== undefined);
}
