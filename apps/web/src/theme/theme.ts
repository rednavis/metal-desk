/** What the customer chose: a theme, or "system" to follow the device. */
export type Theme = "system" | "light" | "dark";

export const THEMES: readonly Theme[] = ["system", "light", "dark"];

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

/** The theme actually shown: a chosen one, or the device's when the customer chose none. */
export function resolveTheme(theme: Theme, prefersDark: boolean): "light" | "dark" {
  if (theme === "system") return prefersDark ? "dark" : "light";
  return theme;
}

/** Switches the whole page by setting the root's `data-theme`; the tokens do the rest. */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset["theme"] = resolveTheme(theme, systemPrefersDark());
}
