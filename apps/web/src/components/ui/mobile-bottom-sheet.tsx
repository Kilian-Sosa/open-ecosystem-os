"use client";

import { X } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

type MobileBottomSheetProps = {
  title: string;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  className?: string;
};

export function MobileBottomSheet({
  title,
  open,
  onClose,
  children,
  className,
}: MobileBottomSheetProps) {
  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-40 md:hidden"
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <button
        type="button"
        aria-label="Close file details"
        className="absolute inset-0 bg-text-primary/30"
        onClick={onClose}
      />
      <section
        className={cn(
          "absolute inset-x-0 bottom-0 max-h-[82vh] overflow-auto rounded-t-card border border-border bg-surface p-5 shadow-card",
          className,
        )}
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="min-w-0 truncate text-sm font-semibold text-text-primary">
            {title}
          </h2>
          <button
            type="button"
            aria-label="Close file details"
            className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-card border border-border text-text-secondary hover:bg-surface-muted hover:text-text-primary"
            onClick={onClose}
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}
