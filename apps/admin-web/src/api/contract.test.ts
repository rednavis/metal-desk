import { describe, expect, it } from "vitest";
import { enumConstants, readJava, recordComponents } from "../test/javaSource";
import { contract } from "./types";

/**
 * The TypeScript mirrors of the server's DTOs against the Java they mirror. This is what keeps
 * `types.ts` from drifting: add, rename or remove a record component or an enum constant on the
 * server and this test fails until the schema says the same.
 */
describe("API types match the Java they mirror", () => {
  for (const entry of contract) {
    if (entry.kind === "record") {
      it(`record ${entry.record} has the same fields`, () => {
        const java = recordComponents(readJava(entry.java), entry.record);
        expect(Object.keys(entry.schema.shape).sort()).toEqual([...java].sort());
      });
    } else {
      it(`enum ${entry.enumName} has the same constants`, () => {
        const java = enumConstants(readJava(entry.java), entry.enumName);
        expect([...entry.schema.options].sort()).toEqual([...java].sort());
      });
    }
  }

  it("notices a field that is missing from the schema", () => {
    const java = recordComponents(
      "public record Sample(String a, @Foo(x = 1) List<Map<String, String>> b, int c) {}",
      "Sample",
    );
    expect(java).toEqual(["a", "b", "c"]);
  });

  it("reads enum constants with arguments and trailing members", () => {
    const java = enumConstants("enum E { ONE(new X(1)), TWO, THREE; private int n; }", "E");
    expect(java).toEqual(["ONE", "TWO", "THREE"]);
  });
});
