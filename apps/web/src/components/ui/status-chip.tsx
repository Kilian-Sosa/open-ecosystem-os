import { CheckCircle2, CircleDashed, Clock3, XCircle } from "lucide-react";

import { cn } from "@/lib/cn";

export type StatusKind =
  | "active"
  | "disabled"
  | "processing"
  | "failed"
  | "completed"
  | "draft"
  | "submitted"
  | "approved"
  | "rejected"
  | "installed"
  | "update-available"
  | "incompatible"
  | "queued"
  | "retrying"
  | "scheduled"
  | "healthy"
  | "running";

const statusStyles: Record<StatusKind, string> = {
  active: "border-success-soft bg-success-soft text-success",
  disabled: "border-border bg-surface-muted text-text-secondary",
  processing: "border-info-soft bg-info-soft text-info",
  failed: "border-danger-soft bg-danger-soft text-danger",
  completed: "border-success-soft bg-success-soft text-success",
  draft: "border-border bg-surface-muted text-text-secondary",
  submitted: "border-info-soft bg-info-soft text-info",
  approved: "border-success-soft bg-success-soft text-success",
  rejected: "border-danger-soft bg-danger-soft text-danger",
  installed: "border-success-soft bg-success-soft text-success",
  "update-available": "border-warning-soft bg-warning-soft text-warning",
  incompatible: "border-danger-soft bg-danger-soft text-danger",
  queued: "border-warning-soft bg-warning-soft text-warning",
  retrying: "border-warning-soft bg-warning-soft text-warning",
  scheduled: "border-info-soft bg-info-soft text-info",
  healthy: "border-success-soft bg-success-soft text-success",
  running: "border-success-soft bg-success-soft text-success",
};

const statusIcons: Partial<Record<StatusKind, typeof CheckCircle2>> = {
  active: CheckCircle2,
  completed: CheckCircle2,
  approved: CheckCircle2,
  healthy: CheckCircle2,
  running: CheckCircle2,
  processing: CircleDashed,
  queued: Clock3,
  retrying: Clock3,
  scheduled: Clock3,
  failed: XCircle,
  rejected: XCircle,
  incompatible: XCircle,
};

type StatusChipProps = {
  status: StatusKind;
  label?: string;
  className?: string;
};

function formatStatus(status: StatusKind) {
  return status
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function StatusChip({
  status,
  label = formatStatus(status),
  className,
}: StatusChipProps) {
  const Icon = statusIcons[status];

  return (
    <span
      className={cn(
        "inline-flex min-h-6 items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium leading-5",
        statusStyles[status],
        className,
      )}
    >
      {Icon ? <Icon aria-hidden="true" className="h-3.5 w-3.5" /> : null}
      {label}
    </span>
  );
}
