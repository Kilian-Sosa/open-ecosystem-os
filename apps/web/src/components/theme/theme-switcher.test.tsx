import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { THEME_ATTRIBUTE, THEME_STORAGE_KEY } from "@/lib/theme";

import { ThemeProvider } from "./theme-provider";
import { ThemeSwitcher } from "./theme-switcher";

function mockMatchMedia(prefersDark: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: prefersDark,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

describe("ThemeSwitcher", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute(THEME_ATTRIBUTE);
    document.documentElement.style.colorScheme = "";
    mockMatchMedia(false);
  });

  it("stores and applies a browser-local dark theme preference", () => {
    render(
      <ThemeProvider>
        <ThemeSwitcher />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("radio", { name: "Dark theme" }));

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(document.documentElement).toHaveAttribute(THEME_ATTRIBUTE, "dark");
    expect(screen.getByRole("radio", { name: "Dark theme" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("applies the system preference from the browser color scheme", () => {
    mockMatchMedia(true);

    render(
      <ThemeProvider>
        <ThemeSwitcher />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("radio", { name: "System theme" }));

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("system");
    expect(document.documentElement).toHaveAttribute(THEME_ATTRIBUTE, "dark");
  });
});
