import { describe, expect, it } from "vitest";
import { memoryStorage } from "../test/render";
import { defaultPreferences } from "./model";
import {
  STORAGE_KEY,
  fromServer,
  loadLocal,
  reconcile,
  saveLocal,
  serverIsMissingSomething,
  toServer,
} from "./persistence";

const local = { theme: "light", locale: "de", currency: "USD" } as const;

describe("local persistence", () => {
  it("round-trips what was saved", () => {
    const storage = memoryStorage();
    saveLocal(storage, local);

    expect(loadLocal(storage)).toEqual(local);
  });

  it("ignores junk and invalid values instead of trusting them", () => {
    const storage = memoryStorage();
    storage.setItem(
      STORAGE_KEY,
      JSON.stringify({ theme: "neon", locale: "xx", currency: "dollars" }),
    );
    expect(loadLocal(storage)).toEqual({});
    storage.setItem(STORAGE_KEY, "{not json");
    expect(loadLocal(storage)).toEqual({});
  });

  it("copes with storage that is not there", () => {
    expect(loadLocal(null)).toEqual({});
    expect(() => {
      saveLocal(null, local);
    }).not.toThrow();
  });
});

describe("the sign-in rule", () => {
  it("lets the server's fields win and keeps the local ones the server lacks", () => {
    const merged = reconcile(local, fromServer({ theme: "DARK" }));

    expect(merged).toEqual({ theme: "dark", locale: "de", currency: "USD" });
  });

  it("knows when the server needs the merged result pushed up", () => {
    const server = fromServer({ theme: "DARK" });
    expect(serverIsMissingSomething(reconcile(local, server), server)).toBe(true);
    const full = fromServer({ theme: "DARK", locale: "de", currency: "USD" });
    expect(serverIsMissingSomething(reconcile(local, full), full)).toBe(false);
  });

  it("sends 'follow the device' to the server as no theme", () => {
    expect(toServer({ ...defaultPreferences(), theme: "system" }).theme).toBeUndefined();
    expect(toServer({ ...defaultPreferences(), theme: "dark" }).theme).toBe("DARK");
  });
});
