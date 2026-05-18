"use client";

import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react";

import { cn } from "@/lib/cn";
import { themePreferences, type ThemePreference } from "@/lib/theme";

import { useTheme } from "./theme-provider";

type ThemeOption = {
  value: ThemePreference;
  label: string;
  icon: LucideIcon;
};

const themeOptions: ThemeOption[] = [
  { value: "light", label: "Light theme", icon: Sun },
  { value: "dark", label: "Dark theme", icon: Moon },
  { value: "system", label: "System theme", icon: Monitor },
];

type ThemeSwitcherProps = {
  className?: string;
};

export function ThemeSwitcher({ className }: ThemeSwitcherProps) {
  const { themePreference, setThemePreference } = useTheme();

  return (
    <div
      aria-label="Theme preference"
      className={cn(
        "inline-flex shrink-0 rounded-card border border-border bg-surface-muted p-1",
        className,
      )}
      role="radiogroup"
    >
      {themeOptions.map((option) => {
        const Icon = option.icon;
        const active = option.value === themePreference;

        return (
          <button
            key={option.value}
            type="button"
            aria-checked={active}
            aria-label={option.label}
            className={cn(
              "inline-flex h-8 w-8 items-center justify-center rounded-card text-text-secondary transition hover:bg-surface hover:text-text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary",
              active && "bg-surface text-primary shadow-card",
            )}
            role="radio"
            title={option.label}
            onClick={() => setThemePreference(option.value)}
          >
            <Icon className="h-4 w-4" aria-hidden="true" />
          </button>
        );
      })}
      <span className="sr-only">
        Available themes: {themePreferences.join(", ")}
      </span>
    </div>
  );
}
