import type { Config } from "tailwindcss";

const withAlpha = (variable: string) => `rgb(var(${variable}) / <alpha-value>)`;

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  darkMode: ["selector", '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        background: withAlpha("--color-background"),
        surface: withAlpha("--color-surface"),
        "surface-muted": withAlpha("--color-surface-muted"),
        "surface-elevated": withAlpha("--color-surface-elevated"),
        border: withAlpha("--color-border"),
        "border-strong": withAlpha("--color-border-strong"),
        "text-primary": withAlpha("--color-text-primary"),
        "text-secondary": withAlpha("--color-text-secondary"),
        "text-muted": withAlpha("--color-text-muted"),
        primary: withAlpha("--color-primary"),
        "primary-soft": withAlpha("--color-primary-soft"),
        "primary-hover": withAlpha("--color-primary-hover"),
        "primary-foreground": withAlpha("--color-primary-foreground"),
        success: withAlpha("--color-success"),
        "success-soft": withAlpha("--color-success-soft"),
        warning: withAlpha("--color-warning"),
        "warning-soft": withAlpha("--color-warning-soft"),
        danger: withAlpha("--color-danger"),
        "danger-soft": withAlpha("--color-danger-soft"),
        info: withAlpha("--color-info"),
        "info-soft": withAlpha("--color-info-soft"),
      },
      fontFamily: {
        sans: ["var(--font-sans)"],
        mono: ["var(--font-mono)"],
      },
      borderRadius: {
        card: "var(--radius-card)",
      },
      boxShadow: {
        card: "var(--shadow-card)",
        "card-hover": "var(--shadow-card-hover)",
      },
      maxWidth: {
        app: "var(--layout-max-width)",
        shell: "var(--layout-max-width)",
      },
    },
  },
  plugins: [],
};

export default config;
