import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type MetricTone = "primary" | "success" | "warning" | "danger" | "info";

const toneStyles: Record<MetricTone, string> = {
  primary: "bg-primary-soft text-primary",
  success: "bg-success-soft text-success",
  warning: "bg-warning-soft text-warning",
  danger: "bg-danger-soft text-danger",
  info: "bg-info-soft text-info",
};

type MetricCardProps = {
  label: string;
  value: string;
  icon: ReactNode;
  tone?: MetricTone;
  trend?: string;
  detail?: string;
  progress?: number;
};

export function MetricCard({
  label,
  value,
  icon,
  tone = "primary",
  trend,
  detail,
  progress,
}: MetricCardProps) {
  return (
    <article className="rounded-card border border-border bg-surface p-5 shadow-card">
      <div className="flex items-start gap-4">
        <div
          className={cn(
            "flex h-11 w-11 shrink-0 items-center justify-center rounded-card",
            toneStyles[tone],
          )}
        >
          {icon}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold uppercase tracking-normal text-text-secondary">
            {label}
          </p>
          <p className="mt-2 text-2xl font-semibold tracking-normal text-text-primary">
            {value}
          </p>
          {trend || detail ? (
            <p className="mt-2 text-sm text-text-secondary">
              {trend ? (
                <span className="font-medium text-success">{trend}</span>
              ) : null}
              {trend && detail ? <span> </span> : null}
              {detail}
            </p>
          ) : null}
          {typeof progress === "number" ? (
            <div
              className="mt-3 h-2 rounded-full bg-surface-muted"
              aria-label={`${label} ${progress}%`}
            >
              <div
                className="h-2 rounded-full bg-primary"
                style={{ width: `${progress}%` }}
              />
            </div>
          ) : null}
        </div>
      </div>
    </article>
  );
}
