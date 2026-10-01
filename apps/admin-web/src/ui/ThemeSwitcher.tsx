import { useEffect, useState } from "react";
import { applyTheme, isTheme, loadTheme, saveTheme, THEMES, type Theme } from "./theme";

const LABEL: Record<Theme, string> = { system: "System", light: "Light", dark: "Dark" };

/**
 * The light/dark switch. "System" follows the device, including when the device changes while the
 * page is open; a chosen theme is remembered in this browser.
 */
export function ThemeSwitcher() {
  const [theme, setTheme] = useState<Theme>(loadTheme);

  useEffect(() => {
    applyTheme(theme);
    if (theme !== "system" || typeof window.matchMedia !== "function") return undefined;
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const follow = () => {
      applyTheme("system");
    };
    query.addEventListener("change", follow);
    return () => {
      query.removeEventListener("change", follow);
    };
  }, [theme]);

  return (
    <label className="md-theme-switch">
      <select
        aria-label="Theme"
        value={theme}
        onChange={(event) => {
          const chosen = event.target.value;
          if (!isTheme(chosen)) return;
          saveTheme(chosen);
          setTheme(chosen);
        }}
      >
        {THEMES.map((choice) => (
          <option key={choice} value={choice}>
            {LABEL[choice]}
          </option>
        ))}
      </select>
    </label>
  );
}
