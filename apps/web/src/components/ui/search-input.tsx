import { Search } from "lucide-react";
import type { InputHTMLAttributes } from "react";

import { cn } from "@/lib/cn";

type SearchInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
  shortcutLabel?: string;
};

export function SearchInput({
  className,
  shortcutLabel,
  ...props
}: SearchInputProps) {
  return (
    <label className={cn("relative block", className)}>
      <span className="sr-only">{props["aria-label"] ?? "Search"}</span>
      <Search
        aria-hidden="true"
        className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
      />
      <input
        type="search"
        className={cn(
          "h-11 w-full rounded-card border border-border bg-surface px-9 text-sm text-text-primary shadow-card transition placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20",
          shortcutLabel ? "pr-16" : "pr-3",
        )}
        {...props}
      />
      {shortcutLabel ? (
        <kbd className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 rounded border border-border bg-surface-muted px-1.5 py-0.5 font-mono text-[11px] text-text-secondary sm:inline-flex">
          {shortcutLabel}
        </kbd>
      ) : null}
    </label>
  );
}
