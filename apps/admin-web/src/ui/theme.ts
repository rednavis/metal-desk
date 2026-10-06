/** What the staff member chose: a theme, or "system" to follow the device. */
export type Theme = "system" | "light" | "dark";

export const THEMES: readonly Theme[] = ["system", "light", "dark"];

const STORAGE_KEY = "metaldesk.admin.theme";

export function isTheme(value: unknown): value is Theme {
  return value === "system" || value === "light" || value === "dark";
}

/** Whether the device asks for a dark interface. */
export function systemPrefersDark(): boolean {
  return (
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-color-scheme: dark)").matches
  );
}

/** The theme actually shown: a chosen one, or the device's when none was chosen. */
export function resolveTheme(theme: Theme, prefersDark: boolean): "light" | "dark" {
  if (theme === "system") return prefersDark ? "dark" : "light";
  return theme;
}

/** Switches the whole page by setting the root's `data-theme`; the tokens do the rest. */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset["theme"] = resolveTheme(theme, systemPrefersDark());
}

/** The saved choice, or "system". Storage can be blocked or empty, so a failure is "no choice". */
export function loadTheme(): Theme {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    return isTheme(saved) ? saved : "system";
  } catch {
    return "system";
  }
}

/** Remembers the choice for next time; losing it is harmless. */
export function saveTheme(theme: Theme): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Storage may be blocked; the choice then lasts until the page is closed.
  }
}
