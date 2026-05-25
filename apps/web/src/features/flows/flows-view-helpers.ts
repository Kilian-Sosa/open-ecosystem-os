import type {
  WorkflowActionType,
  WorkflowExecutionStatus,
  WorkflowStatus,
  WorkflowSummary,
  WorkflowTrigger,
} from "@/lib/flows-api";
import type { FlowsState } from "@/lib/flows-mock-data";

export function resolveFlowsState(
  override: FlowsState | undefined,
  loading: boolean,
  error: boolean,
  workflows: WorkflowSummary[],
): FlowsState {
  if (override) return override;
  if (loading) return "loading";
  if (error) return "error";
  return workflows.length === 0 ? "empty" : "normal";
}

export function workflowStatusChip(status: WorkflowStatus) {
  if (status === "active") return "active";
  if (status === "paused") return "disabled";
  return "draft";
}

export function executionStatusChip(status: WorkflowExecutionStatus) {
  if (status === "running") return "processing";
  return status;
}

export function triggerTitle(trigger: WorkflowTrigger) {
  return trigger.type === "event" ? trigger.eventType : "Manual trigger";
}

export function nodeTone(
  actionType: WorkflowActionType,
): "info" | "success" | "warning" {
  if (actionType === "create_notification") return "info";
  if (actionType === "create_audit_entry") return "warning";
  if (actionType === "request_search_indexing") return "info";
  return "success";
}

export function formatActionType(actionType: WorkflowActionType) {
  return actionType
    .replace("create_", "")
    .replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function formatFlowDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone: "UTC",
  }).format(new Date(value));
}
