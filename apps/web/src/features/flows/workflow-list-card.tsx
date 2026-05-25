import { SectionCard, StatusChip } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { WorkflowSummary } from "@/lib/flows-api";
import { workflowStatusChip } from "./flows-view-helpers";

type WorkflowListCardProps = {
  workflows: WorkflowSummary[];
  selectedWorkflowId: string | null;
  onSelectWorkflow: (workflow: WorkflowSummary) => void;
};

export function WorkflowListCard({
  workflows,
  selectedWorkflowId,
  onSelectWorkflow,
}: WorkflowListCardProps) {
  return (
    <SectionCard title="Workflows" description="Workspace automation drafts.">
      <div className="space-y-3">
        {workflows.map((workflow) => (
          <button
            key={workflow.workflowId}
            type="button"
            className={cn(
              "w-full rounded-card border border-border bg-surface p-3 text-left hover:bg-surface-muted",
              selectedWorkflowId === workflow.workflowId && "bg-primary-soft",
            )}
            onClick={() => onSelectWorkflow(workflow)}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-text-primary">
                  {workflow.name}
                </p>
                <p className="mt-1 text-xs text-text-secondary">
                  v{workflow.currentVersionNumber} - {workflow.stepCount} steps
                </p>
              </div>
              <StatusChip
                status={workflowStatusChip(workflow.status)}
                label={workflow.status}
              />
            </div>
          </button>
        ))}
      </div>
    </SectionCard>
  );
}
