import { describe, expect, it } from "vitest";

import {
  DEFAULT_THEME_PREFERENCE,
  parseThemePreference,
  resolveThemePreference,
} from "./theme";

describe("theme helpers", () => {
  it("parses known theme preferences", () => {
    expect(parseThemePreference("light")).toBe("light");
    expect(parseThemePreference("dark")).toBe("dark");
    expect(parseThemePreference("system")).toBe("system");
  });

  it("falls back to system for unknown preferences", () => {
    expect(parseThemePreference("midnight")).toBe(DEFAULT_THEME_PREFERENCE);
    expect(parseThemePreference(undefined)).toBe(DEFAULT_THEME_PREFERENCE);
  });

  it("resolves system preference from the browser color scheme", () => {
    expect(resolveThemePreference("system", true)).toBe("dark");
    expect(resolveThemePreference("system", false)).toBe("light");
  });

  it("resolves explicit preferences without using the browser color scheme", () => {
    expect(resolveThemePreference("light", true)).toBe("light");
    expect(resolveThemePreference("dark", false)).toBe("dark");
  });
});
