import { ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";

type PermissionDeniedStateProps = {
  title: string;
  description: string;
  action?: ReactNode;
};

export function PermissionDeniedState({
  title,
  description,
  action,
}: PermissionDeniedStateProps) {
  return (
    <section className="flex min-h-80 flex-col items-center justify-center rounded-card border border-border bg-surface p-8 text-center shadow-card">
      <div className="flex h-12 w-12 items-center justify-center rounded-card bg-warning-soft text-warning">
        <ShieldAlert aria-hidden="true" className="h-6 w-6" />
      </div>
      <h2 className="mt-4 text-lg font-semibold text-text-primary">{title}</h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-text-secondary">
        {description}
      </p>
      {action ? <div className="mt-5">{action}</div> : null}
    </section>
  );
}
