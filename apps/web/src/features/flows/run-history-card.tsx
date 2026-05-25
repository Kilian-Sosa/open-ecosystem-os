import { SectionCard, StatusChip } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { WorkflowExecutionSummary } from "@/lib/flows-api";
import { executionStatusChip, formatFlowDate } from "./flows-view-helpers";

type RunHistoryCardProps = {
  executions: WorkflowExecutionSummary[];
  selectedExecutionId: string | null;
  onSelectExecution: (execution: WorkflowExecutionSummary) => void;
};

export function RunHistoryCard({
  executions,
  selectedExecutionId,
  onSelectExecution,
}: RunHistoryCardProps) {
  return (
    <SectionCard title="Run history" description="Latest workflow executions.">
      {executions.length === 0 ? (
        <p className="text-sm text-text-secondary">
          No runs have completed yet.
        </p>
      ) : (
        <div className="space-y-3">
          {executions.map((execution) => (
            <button
              key={execution.executionId}
              type="button"
              className={cn(
                "w-full rounded-card border border-border bg-surface p-3 text-left hover:bg-surface-muted",
                selectedExecutionId === execution.executionId &&
                  "bg-primary-soft",
              )}
              onClick={() => onSelectExecution(execution)}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-text-primary">
                    {execution.workflowName}
                  </p>
                  <p className="mt-1 text-xs text-text-secondary">
                    {execution.triggerType} -{" "}
                    {formatFlowDate(execution.updatedAt)}
                  </p>
                </div>
                <StatusChip
                  status={executionStatusChip(execution.status)}
                  label={execution.status}
                />
              </div>
            </button>
          ))}
        </div>
      )}
    </SectionCard>
  );
}
