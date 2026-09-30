/**
 * Where preferences are kept, and which copy wins.
 *
 * - **Anonymous visitor:** `localStorage`, so the choice survives a reload. Nothing here is
 *   sensitive, which is why, unlike the bearer token, it may live there.
 * - **Signed-in customer:** also the server (`/account/preferences`), so the choice follows the
 *   account to another device (BRD FR-1.6: "persists across sessions").
 *
 * **On sign-in the server wins, field by field.** A field the server has overrides the local one;
 * a field only the local copy has is kept and pushed up, so nothing the customer chose is lost and
 * the server value is never overwritten by a stale browser. The opposite rule (local wins) is as
 * defensible but would make the server copy unreachable from any browser that already has a
 * choice, which defeats the point of keeping it there; whichever is chosen, it must be the only
 * rule, or the two copies fight.
 */
import type { ApiClient } from "../api/client";
import { preferencesViewSchema, type PreferencesView } from "../api/types";
import { isLocale } from "../i18n/locales";
import { isTheme } from "../theme/theme";
import type { PartialPreferences, Preferences } from "./model";

export const STORAGE_KEY = "metaldesk.preferences";

/** Reads what was stored locally, ignoring anything that is not a valid choice. */
export function loadLocal(storage: Storage | null): PartialPreferences {
  try {
    const raw = storage?.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const loaded: PartialPreferences = {};
    if (isTheme(parsed["theme"])) loaded.theme = parsed["theme"];
    if (isLocale(parsed["locale"])) loaded.locale = parsed["locale"];
    if (typeof parsed["currency"] === "string" && /^[A-Z]{3}$/.test(parsed["currency"])) {
      loaded.currency = parsed["currency"];
    }
    return loaded;
  } catch {
    return {};
  }
}

/** Stores the choice locally; a browser that refuses storage just forgets it on reload. */
export function saveLocal(storage: Storage | null, preferences: Preferences): void {
  try {
    storage?.setItem(STORAGE_KEY, JSON.stringify(preferences));
  } catch {
    // Storage full or blocked: the choice still applies for this page view.
  }
}

/** The server's copy as preferences; "no theme" on the server is "follow the device". */
export function fromServer(view: PreferencesView): PartialPreferences {
  const loaded: PartialPreferences = {};
  if (view.theme) loaded.theme = view.theme === "DARK" ? "dark" : "light";
  if (isLocale(view.locale)) loaded.locale = view.locale;
  if (view.currency) loaded.currency = view.currency;
  return loaded;
}

/** What to send the server: "follow the device" is sent as no theme. */
export function toServer(preferences: Preferences): PreferencesView {
  return {
    theme:
      preferences.theme === "system" ? undefined : preferences.theme === "dark" ? "DARK" : "LIGHT",
    locale: preferences.locale,
    currency: preferences.currency,
  };
}

/** The sign-in rule: the server's fields win, the local ones fill what the server lacks. */
export function reconcile(local: Preferences, server: PartialPreferences): Preferences {
  return { ...local, ...server };
}

/** Whether the server lacks something the merged result has, so that it needs pushing up. */
export function serverIsMissingSomething(merged: Preferences, server: PartialPreferences): boolean {
  return (Object.keys(merged) as (keyof Preferences)[]).some((key) => server[key] === undefined);
}

export async function fetchServerPreferences(client: ApiClient): Promise<PartialPreferences> {
  const view = await client.get("/account/preferences", { schema: preferencesViewSchema });
  return fromServer(view);
}

export async function saveServerPreferences(
  client: ApiClient,
  preferences: Preferences,
): Promise<void> {
  await client.put("/account/preferences", {
    body: toServer(preferences),
    schema: preferencesViewSchema,
  });
}
