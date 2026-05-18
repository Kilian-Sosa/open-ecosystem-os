import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type ActivityItemProps = {
  icon: ReactNode;
  title: string;
  meta?: string;
  time: string;
  className?: string;
};

export function ActivityItem({
  icon,
  title,
  meta,
  time,
  className,
}: ActivityItemProps) {
  return (
    <article className={cn("flex items-start gap-3", className)}>
      <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-card bg-surface-muted text-text-secondary">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium leading-5 text-text-primary">
          {title}
        </p>
        {meta ? (
          <p className="mt-0.5 text-xs leading-5 text-text-secondary">{meta}</p>
        ) : null}
      </div>
      <time className="shrink-0 text-xs text-text-muted">{time}</time>
    </article>
  );
}
