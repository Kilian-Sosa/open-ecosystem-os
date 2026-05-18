import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type QuickActionTone = "primary" | "success" | "warning" | "info";

const toneStyles: Record<QuickActionTone, string> = {
  primary: "bg-primary-soft text-primary",
  success: "bg-success-soft text-success",
  warning: "bg-warning-soft text-warning",
  info: "bg-info-soft text-info",
};

type QuickActionCardProps = {
  title: string;
  description: string;
  icon: ReactNode;
  tone?: QuickActionTone;
};

export function QuickActionCard({
  title,
  description,
  icon,
  tone = "primary",
}: QuickActionCardProps) {
  return (
    <button
      type="button"
      className="group flex min-h-36 flex-col items-start rounded-card border border-border bg-surface p-4 text-left shadow-card transition hover:border-border-strong hover:bg-surface-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <span
        className={cn(
          "flex h-10 w-10 items-center justify-center rounded-card",
          toneStyles[tone],
        )}
      >
        {icon}
      </span>
      <span className="mt-4 text-sm font-semibold text-text-primary">
        {title}
      </span>
      <span className="mt-1 text-sm leading-5 text-text-secondary">
        {description}
      </span>
    </button>
  );
}
