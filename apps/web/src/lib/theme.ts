export const THEME_STORAGE_KEY = "open-ecosystem-os:theme-preference";
export const THEME_PREFERENCE_CHANGE_EVENT =
  "open-ecosystem-os:theme-preference-change";
export const THEME_ATTRIBUTE = "data-theme";
export const THEME_SYSTEM_QUERY = "(prefers-color-scheme: dark)";

export const themePreferences = ["light", "dark", "system"] as const;

export type ThemePreference = (typeof themePreferences)[number];
export type ResolvedTheme = Exclude<ThemePreference, "system">;

export const DEFAULT_THEME_PREFERENCE: ThemePreference = "system";
export const DEFAULT_RESOLVED_THEME: ResolvedTheme = "light";

export function isThemePreference(value: unknown): value is ThemePreference {
  return (
    typeof value === "string" &&
    themePreferences.includes(value as ThemePreference)
  );
}

export function parseThemePreference(value: unknown): ThemePreference {
  return isThemePreference(value) ? value : DEFAULT_THEME_PREFERENCE;
}

export function resolveThemePreference(
  preference: ThemePreference,
  systemPrefersDark: boolean,
): ResolvedTheme {
  if (preference === "system") {
    return systemPrefersDark ? "dark" : "light";
  }

  return preference;
}

export function readThemePreferenceFromStorage(
  storage: Pick<Storage, "getItem">,
): ThemePreference {
  try {
    return parseThemePreference(storage.getItem(THEME_STORAGE_KEY));
  } catch {
    return DEFAULT_THEME_PREFERENCE;
  }
}

export function writeThemePreferenceToStorage(
  storage: Pick<Storage, "setItem">,
  preference: ThemePreference,
) {
  try {
    storage.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // Storage can be unavailable in privacy modes; the active document theme still applies.
  }
}

export function applyResolvedTheme(
  root: HTMLElement,
  resolvedTheme: ResolvedTheme,
) {
  root.setAttribute(THEME_ATTRIBUTE, resolvedTheme);
  root.style.colorScheme = resolvedTheme;
}
