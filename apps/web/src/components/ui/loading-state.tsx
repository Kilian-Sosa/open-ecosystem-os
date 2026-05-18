import { cn } from "@/lib/cn";

type LoadingStateProps = {
  label?: string;
  className?: string;
};

export function LoadingState({
  label = "Loading workspace dashboard",
  className,
}: LoadingStateProps) {
  return (
    <section
      aria-label={label}
      aria-busy="true"
      className={cn(
        "grid gap-4 rounded-card border border-border bg-surface p-5 shadow-card",
        className,
      )}
    >
      <div className="h-5 w-48 animate-pulse rounded bg-surface-muted" />
      <div className="grid gap-3 md:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <div
            key={index}
            className="h-28 animate-pulse rounded-card bg-surface-muted"
          />
        ))}
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <div className="h-56 animate-pulse rounded-card bg-surface-muted" />
        <div className="h-56 animate-pulse rounded-card bg-surface-muted" />
      </div>
    </section>
  );
}
