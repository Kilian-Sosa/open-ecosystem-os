"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";

import {
  DEFAULT_THEME_PREFERENCE,
  THEME_PREFERENCE_CHANGE_EVENT,
  THEME_STORAGE_KEY,
  THEME_SYSTEM_QUERY,
  applyResolvedTheme,
  readThemePreferenceFromStorage,
  resolveThemePreference,
  writeThemePreferenceToStorage,
  type ResolvedTheme,
  type ThemePreference,
} from "@/lib/theme";

type ThemeContextValue = {
  themePreference: ThemePreference;
  resolvedTheme: ResolvedTheme;
  setThemePreference: (preference: ThemePreference) => void;
};

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

type ThemeProviderProps = {
  children: ReactNode;
};

function getThemePreferenceSnapshot() {
  return readThemePreferenceFromStorage(window.localStorage);
}

function getThemePreferenceServerSnapshot() {
  return DEFAULT_THEME_PREFERENCE;
}

function subscribeThemePreference(onStoreChange: () => void) {
  const handleStorageChange = (event: StorageEvent) => {
    if (event.key === THEME_STORAGE_KEY) {
      onStoreChange();
    }
  };

  window.addEventListener("storage", handleStorageChange);
  window.addEventListener(THEME_PREFERENCE_CHANGE_EVENT, onStoreChange);

  return () => {
    window.removeEventListener("storage", handleStorageChange);
    window.removeEventListener(THEME_PREFERENCE_CHANGE_EVENT, onStoreChange);
  };
}

function getSystemThemeSnapshot() {
  return window.matchMedia(THEME_SYSTEM_QUERY).matches;
}

function getSystemThemeServerSnapshot() {
  return false;
}

function subscribeSystemTheme(onStoreChange: () => void) {
  const mediaQuery = window.matchMedia(THEME_SYSTEM_QUERY);

  mediaQuery.addEventListener("change", onStoreChange);

  return () => {
    mediaQuery.removeEventListener("change", onStoreChange);
  };
}

function getResolvedTheme(preference: ThemePreference) {
  return resolveThemePreference(preference, getSystemThemeSnapshot());
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const themePreference = useSyncExternalStore(
    subscribeThemePreference,
    getThemePreferenceSnapshot,
    getThemePreferenceServerSnapshot,
  );
  const systemPrefersDark = useSyncExternalStore(
    subscribeSystemTheme,
    getSystemThemeSnapshot,
    getSystemThemeServerSnapshot,
  );
  const resolvedTheme = resolveThemePreference(
    themePreference,
    systemPrefersDark,
  );

  useEffect(() => {
    applyResolvedTheme(document.documentElement, resolvedTheme);
  }, [resolvedTheme]);

  const setThemePreference = useCallback((preference: ThemePreference) => {
    writeThemePreferenceToStorage(window.localStorage, preference);
    applyResolvedTheme(document.documentElement, getResolvedTheme(preference));
    window.dispatchEvent(new Event(THEME_PREFERENCE_CHANGE_EVENT));
  }, []);

  const value = useMemo(
    () => ({
      themePreference,
      resolvedTheme,
      setThemePreference,
    }),
    [resolvedTheme, setThemePreference, themePreference],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

export function useTheme() {
  const value = useContext(ThemeContext);

  if (!value) {
    throw new Error("useTheme must be used within ThemeProvider");
  }

  return value;
}
