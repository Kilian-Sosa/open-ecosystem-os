import type {
  WorkflowDetail,
  WorkflowExecutionDetail,
  WorkflowExecutionSummary,
  WorkflowSummary,
} from "@/lib/flows-api";
import { RunHistoryCard } from "./run-history-card";
import { WorkflowBuilderCard } from "./workflow-builder-card";
import { WorkflowListCard } from "./workflow-list-card";

type FlowsNormalStateProps = {
  workflows: WorkflowSummary[];
  executions: WorkflowExecutionSummary[];
  selectedWorkflowSummary: WorkflowSummary | null;
  selectedWorkflow: WorkflowDetail | undefined | null;
  selectedExecution: WorkflowExecutionSummary | WorkflowExecutionDetail | null;
  runError: boolean;
  onSelectWorkflow: (workflow: WorkflowSummary) => void;
  onSelectExecution: (execution: WorkflowExecutionSummary) => void;
};

export function FlowsNormalState({
  workflows,
  executions,
  selectedWorkflowSummary,
  selectedWorkflow,
  selectedExecution,
  runError,
  onSelectWorkflow,
  onSelectExecution,
}: FlowsNormalStateProps) {
  const completed = executions.filter(
    (execution) => execution.status === "completed",
  ).length;
  const failed = executions.filter(
    (execution) => execution.status === "failed",
  ).length;
  const active = workflows.filter(
    (workflow) => workflow.status === "active",
  ).length;

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <FlowMetric
          label="Workflows"
          value={workflows.length.toString()}
          detail={`${active} active`}
        />
        <FlowMetric
          label="Executions"
          value={executions.length.toString()}
          detail={`${completed} completed`}
        />
        <FlowMetric
          label="Failures"
          value={failed.toString()}
          detail="Retry counts persisted"
        />
      </section>

      {runError ? (
        <div className="rounded-card border border-danger-soft bg-danger-soft p-4 text-sm text-danger">
          Workflow run failed. Check the API and try again.
        </div>
      ) : null}

      <section className="grid gap-6 xl:grid-cols-[280px_minmax(0,1fr)_360px]">
        <WorkflowListCard
          workflows={workflows}
          selectedWorkflowId={selectedWorkflowSummary?.workflowId ?? null}
          onSelectWorkflow={onSelectWorkflow}
        />
        <WorkflowBuilderCard
          workflowSummary={selectedWorkflowSummary}
          workflow={selectedWorkflow}
        />
        <RunHistoryCard
          executions={executions}
          selectedExecutionId={selectedExecution?.executionId ?? null}
          onSelectExecution={onSelectExecution}
        />
      </section>
    </div>
  );
}

function FlowMetric({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <div className="rounded-card border border-border bg-surface p-4 shadow-card">
      <p className="text-xs font-medium uppercase tracking-normal text-text-muted">
        {label}
      </p>
      <p className="mt-2 text-2xl font-semibold text-text-primary">{value}</p>
      <p className="mt-1 truncate text-sm text-text-secondary">{detail}</p>
    </div>
  );
}
