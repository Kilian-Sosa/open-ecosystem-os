import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type PageHeaderProps = {
  title: string;
  subtitle: string;
  primaryAction?: ReactNode;
  secondaryAction?: ReactNode;
  chips?: ReactNode;
  className?: string;
};

export function PageHeader({
  title,
  subtitle,
  primaryAction,
  secondaryAction,
  chips,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex flex-col gap-4 md:flex-row md:items-start md:justify-between",
        className,
      )}
    >
      <div className="min-w-0 space-y-2">
        {chips ? <div className="flex flex-wrap gap-2">{chips}</div> : null}
        <div>
          <h1 className="text-2xl font-semibold tracking-normal text-text-primary md:text-3xl">
            {title}
          </h1>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-text-secondary">
            {subtitle}
          </p>
        </div>
      </div>
      {primaryAction || secondaryAction ? (
        <div className="flex flex-wrap items-center gap-2 md:justify-end">
          {secondaryAction}
          {primaryAction}
        </div>
      ) : null}
    </header>
  );
}
