import { useContext } from "react";
import { PreferencesContext, type PreferencesValue } from "./PreferencesContext";

/**
 * The one hook every screen uses for theme, language and currency, and for `t` and `format`, which
 * are bound to the active language. There is no component-local copy of any preference.
 */
export function usePreferences(): PreferencesValue {
  const value = useContext(PreferencesContext);
  if (value === null) throw new Error("usePreferences must be used inside PreferencesProvider");
  return value;
}
