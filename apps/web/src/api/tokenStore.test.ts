import { describe, expect, it, vi } from "vitest";
import { createPersistentTokenStore } from "./tokenStore";

function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() {
      return data.size;
    },
    clear: () => {
      data.clear();
    },
    getItem: (key) => data.get(key) ?? null,
    key: (index) => [...data.keys()][index] ?? null,
    removeItem: (key) => {
      data.delete(key);
    },
    setItem: (key, value) => {
      data.set(key, value);
    },
  };
}

describe("the persistent token store", () => {
  it("keeps the token in storage, so a new store (a reload) finds it", () => {
    const storage = memoryStorage();
    createPersistentTokenStore(storage, "k").set("abc");

    expect(createPersistentTokenStore(storage, "k").get()).toBe("abc");
  });

  it("forgets it on clear, and tells its listeners about each change", () => {
    const storage = memoryStorage();
    const store = createPersistentTokenStore(storage, "k");
    const listener = vi.fn();
    store.subscribe(listener);

    store.set("abc");
    store.clear();
    store.clear();

    expect(store.get()).toBeNull();
    expect(storage.getItem("k")).toBeNull();
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it("follows a change made in another tab", () => {
    const storage = memoryStorage();
    const store = createPersistentTokenStore(storage, "k");
    const listener = vi.fn();
    store.subscribe(listener);

    storage.setItem("k", "from-another-tab");
    window.dispatchEvent(new StorageEvent("storage", { key: "k" }));

    expect(store.get()).toBe("from-another-tab");
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("keeps working in memory when storage is blocked", () => {
    const blocked = {
      getItem: () => {
        throw new Error("blocked");
      },
      setItem: () => {
        throw new Error("blocked");
      },
      removeItem: () => {
        throw new Error("blocked");
      },
    } as unknown as Storage;
    const store = createPersistentTokenStore(blocked, "k");

    store.set("abc");

    expect(store.get()).toBe("abc");
    store.clear();
    expect(store.get()).toBeNull();
  });
});
