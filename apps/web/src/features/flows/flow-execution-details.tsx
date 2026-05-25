import { RightInspectorPanel, StatusChip } from "@/components/ui";
import type {
  WorkflowExecutionDetail,
  WorkflowExecutionSummary,
} from "@/lib/flows-api";
import {
  executionStatusChip,
  formatActionType,
  formatFlowDate,
} from "./flows-view-helpers";

type FlowExecutionProps = {
  execution: WorkflowExecutionSummary | WorkflowExecutionDetail;
  detailLoading: boolean;
};

export function FlowExecutionInspector({
  execution,
  detailLoading,
}: FlowExecutionProps) {
  return (
    <RightInspectorPanel
      title="Execution detail"
      description="Selected workflow run state and step outputs."
    >
      <FlowExecutionDetails
        execution={execution}
        detailLoading={detailLoading}
      />
    </RightInspectorPanel>
  );
}

export function FlowExecutionDetails({
  execution,
  detailLoading,
}: FlowExecutionProps) {
  const steps = "steps" in execution ? execution.steps : [];

  return (
    <div className="space-y-5">
      <div className="rounded-card border border-border bg-surface-muted p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-text-primary">
              {execution.workflowName}
            </p>
            <p className="mt-1 text-xs text-text-secondary">
              v{execution.workflowVersionNumber} - {execution.triggerType}
            </p>
          </div>
          <StatusChip
            status={executionStatusChip(execution.status)}
            label={execution.status}
          />
        </div>
      </div>

      <dl className="space-y-3 text-sm">
        <DetailRow
          label="Started"
          value={formatFlowDate(execution.startedAt)}
        />
        <DetailRow
          label="Updated"
          value={formatFlowDate(execution.updatedAt)}
        />
        <DetailRow label="Retries" value={execution.retryCount.toString()} />
        <DetailRow label="Correlation" value={execution.correlationId} />
      </dl>

      {execution.failureReason ? (
        <div className="rounded-card border border-danger-soft bg-danger-soft p-4">
          <p className="text-sm font-semibold text-danger">Failure reason</p>
          <p className="mt-2 text-sm leading-5 text-danger">
            {execution.failureReason}
          </p>
        </div>
      ) : null}

      <div className="space-y-3">
        <p className="text-sm font-semibold text-text-primary">Steps</p>
        {detailLoading ? (
          <p className="text-sm text-text-secondary">Loading step details...</p>
        ) : steps.length === 0 ? (
          <p className="text-sm text-text-secondary">
            Step details are not loaded yet.
          </p>
        ) : (
          steps.map((step) => (
            <div
              key={step.stepExecutionId}
              className="rounded-card border border-border bg-surface p-3"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-text-primary">
                    {step.stepName}
                  </p>
                  <p className="mt-1 text-xs text-text-secondary">
                    {formatActionType(step.actionType)}
                  </p>
                </div>
                <StatusChip
                  status={executionStatusChip(step.status)}
                  label={step.status}
                />
              </div>
              {step.failureReason ? (
                <p className="mt-3 text-sm text-danger">{step.failureReason}</p>
              ) : null}
              <pre className="mt-3 max-h-32 overflow-auto rounded-card bg-surface-muted p-3 text-xs leading-5 text-text-secondary">
                {JSON.stringify(step.output, null, 2)}
              </pre>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-text-secondary">{label}</dt>
      <dd className="break-all text-right font-medium text-text-primary">
        {value}
      </dd>
    </div>
  );
}
