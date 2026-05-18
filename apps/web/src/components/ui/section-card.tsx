import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type SectionCardProps = {
  title?: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
};

export function SectionCard({
  title,
  description,
  action,
  children,
  className,
  contentClassName,
}: SectionCardProps) {
  return (
    <section
      className={cn(
        "rounded-card border border-border bg-surface p-5 shadow-card",
        className,
      )}
    >
      {title || description || action ? (
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="min-w-0">
            {title ? (
              <h2 className="text-sm font-semibold text-text-primary">
                {title}
              </h2>
            ) : null}
            {description ? (
              <p className="mt-1 text-sm leading-5 text-text-secondary">
                {description}
              </p>
            ) : null}
          </div>
          {action ? <div className="shrink-0">{action}</div> : null}
        </div>
      ) : null}
      <div className={contentClassName}>{children}</div>
    </section>
  );
}
