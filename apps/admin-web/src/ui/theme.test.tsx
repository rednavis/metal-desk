import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ThemeSwitcher } from "./ThemeSwitcher";
import { applyTheme, loadTheme, resolveTheme, saveTheme } from "./theme";

beforeEach(() => {
  window.localStorage.clear();
  delete document.documentElement.dataset["theme"];
});

afterEach(() => {
  window.localStorage.clear();
});

describe("the theme", () => {
  it("resolves 'system' from the device and a chosen theme as it is", () => {
    expect(resolveTheme("system", true)).toBe("dark");
    expect(resolveTheme("system", false)).toBe("light");
    expect(resolveTheme("light", true)).toBe("light");
    expect(resolveTheme("dark", false)).toBe("dark");
  });

  it("applies the theme to the root, where the tokens read it", () => {
    applyTheme("dark");
    expect(document.documentElement.dataset["theme"]).toBe("dark");
    applyTheme("light");
    expect(document.documentElement.dataset["theme"]).toBe("light");
  });

  it("remembers a choice, and ignores a saved value that is not a theme", () => {
    expect(loadTheme()).toBe("system");
    saveTheme("dark");
    expect(loadTheme()).toBe("dark");
    window.localStorage.setItem("metaldesk.admin.theme", "sepia");
    expect(loadTheme()).toBe("system");
  });
});

describe("the theme switch", () => {
  it("switches the page and remembers the choice", async () => {
    render(<ThemeSwitcher />);

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Theme" }), "dark");

    expect(document.documentElement.dataset["theme"]).toBe("dark");
    expect(loadTheme()).toBe("dark");

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Theme" }), "light");

    expect(document.documentElement.dataset["theme"]).toBe("light");
  });

  it("starts from the saved choice", () => {
    saveTheme("dark");

    render(<ThemeSwitcher />);

    expect(screen.getByRole("combobox", { name: "Theme" })).toHaveValue("dark");
    expect(document.documentElement.dataset["theme"]).toBe("dark");
  });
});
