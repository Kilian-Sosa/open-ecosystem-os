import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type RightInspectorPanelProps = {
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
};

export function RightInspectorPanel({
  title,
  description,
  children,
  className,
}: RightInspectorPanelProps) {
  return (
    <aside
      aria-label={title}
      className={cn(
        "rounded-t-card border border-border bg-surface p-5 shadow-card md:sticky md:top-24 md:max-h-[calc(100vh-7rem)] md:w-[var(--layout-right-panel-width)] md:overflow-auto md:rounded-card",
        className,
      )}
    >
      <div className="mb-5">
        <h2 className="text-sm font-semibold text-text-primary">{title}</h2>
        {description ? (
          <p className="mt-1 text-sm leading-5 text-text-secondary">
            {description}
          </p>
        ) : null}
      </div>
      {children}
    </aside>
  );
}
