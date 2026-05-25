"use client";

import { Play, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { AppShell } from "@/components/layout/app-shell";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  MobileBottomSheet,
  PageHeader,
  PermissionDeniedState,
  StatusChip,
} from "@/components/ui";
import {
  flowsMockExecutionDetails,
  flowsMockExecutions,
  flowsMockWorkflowDetails,
  flowsMockWorkflows,
  type FlowsState,
} from "@/lib/flows-mock-data";
import {
  useRunWorkflow,
  useWorkflow,
  useWorkflowExecution,
  useWorkflowExecutions,
  useWorkflows,
} from "./use-flows";
import {
  FlowExecutionDetails,
  FlowExecutionInspector,
} from "./flow-execution-details";
import { FlowsNormalState } from "./flows-normal-state";
import { resolveFlowsState } from "./flows-view-helpers";

type FlowsScreenProps = {
  initialCorrelationId?: string;
  initialExecutionId?: string;
  initialWorkflowId?: string;
  stateOverride?: FlowsState;
};

export function FlowsScreen({
  initialCorrelationId,
  initialExecutionId,
  initialWorkflowId,
  stateOverride,
}: FlowsScreenProps) {
  const [liveQueriesReady, setLiveQueriesReady] = useState(false);
  const liveQueriesEnabled = stateOverride === undefined && liveQueriesReady;
  const workflowsQuery = useWorkflows(liveQueriesEnabled);
  const executionsQuery = useWorkflowExecutions(liveQueriesEnabled);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string | null>(
    initialWorkflowId ?? null,
  );
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(
    initialExecutionId ?? null,
  );
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
  const runMutation = useRunWorkflow((execution) => {
    setSelectedExecutionId(execution.executionId);
    setMobileSheetOpen(true);
  });

  useEffect(() => {
    const timeout = window.setTimeout(() => setLiveQueriesReady(true), 0);

    return () => window.clearTimeout(timeout);
  }, []);

  const workflows = useMemo(() => {
    if (stateOverride === "normal")
      return flowsMockWorkflows;
    if (stateOverride === "empty")
      return [];
    return workflowsQuery.data?.workflows ?? [];
  }, [stateOverride, workflowsQuery.data?.workflows]);

  const executions = useMemo(() => {
    if (stateOverride === "normal")
      return flowsMockExecutions;
    if (stateOverride === "empty")
      return [];
    return executionsQuery.data?.executions ?? [];
  }, [executionsQuery.data?.executions, stateOverride]);

  useEffect(() => {
    if (selectedExecutionId || !initialCorrelationId) {
      return;
    }

    const linkedExecution = executions.find(
      (execution) => execution.correlationId === initialCorrelationId,
    );
    if (linkedExecution) {
      setSelectedExecutionId(linkedExecution.executionId);
    }
  }, [executions, initialCorrelationId, selectedExecutionId]);

  const selectedWorkflowSummary =
    workflows.find((workflow) => workflow.workflowId === selectedWorkflowId) ??
    workflows[0] ??
    null;
  const selectedWorkflowQuery = useWorkflow(
    selectedWorkflowSummary?.workflowId ?? null,
    liveQueriesEnabled && selectedWorkflowSummary !== null,
  );
  const selectedWorkflow =
    stateOverride === "normal"
      ? (flowsMockWorkflowDetails.find(
          (workflow) =>
            workflow.workflowId === selectedWorkflowSummary?.workflowId,
        ) ?? null)
      : selectedWorkflowQuery.data;

  const selectedExecutionSummary =
    executions.find(
      (execution) => execution.executionId === selectedExecutionId,
    ) ??
    executions[0] ??
    null;
  const selectedExecutionQuery = useWorkflowExecution(
    selectedExecutionSummary?.executionId ?? null,
    liveQueriesEnabled && selectedExecutionSummary !== null,
  );
  const selectedExecution =
    stateOverride === "normal"
      ? (flowsMockExecutionDetails.find(
          (execution) =>
            execution.executionId === selectedExecutionSummary?.executionId,
        ) ?? selectedExecutionSummary)
      : (selectedExecutionQuery.data ?? selectedExecutionSummary);
  const detailLoading =
    stateOverride === undefined &&
    selectedExecutionSummary !== null &&
    selectedExecutionQuery.isPending;
  const state = resolveFlowsState(
    stateOverride,
    stateOverride === undefined &&
      (!liveQueriesReady ||
        workflowsQuery.isPending ||
        executionsQuery.isPending),
    workflowsQuery.isError || executionsQuery.isError,
    workflows,
  );
  const inspector =
    state === "normal" && selectedExecution ? (
      <FlowExecutionInspector
        execution={selectedExecution}
        detailLoading={detailLoading}
      />
    ) : undefined;

  function handleRunWorkflow() {
    if (!selectedWorkflowSummary) {
      return;
    }
    runMutation.mutate(selectedWorkflowSummary.workflowId);
  }

  return (
    <AppShell activeHref="/app/flows" inspector={inspector}>
      <div className="space-y-6">
        <PageHeader
          title="Open Ecosystem Flows"
          subtitle="Build, run, and inspect the first event-driven automation path."
          chips={
            <>
              <StatusChip status="active" label="MVP engine" />
              <StatusChip status="queued" label="OcrCompleted trigger" />
            </>
          }
          primaryAction={
            <button
              type="button"
              className="inline-flex min-h-10 items-center gap-2 rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
              disabled={!selectedWorkflowSummary || runMutation.isPending}
              onClick={handleRunWorkflow}
            >
              <Play className="h-4 w-4" aria-hidden="true" />
              {runMutation.isPending ? "Running..." : "Run workflow"}
            </button>
          }
        />

        {state === "loading" ? (
          <LoadingState label="Loading workflows" />
        ) : state === "empty" ? (
          <EmptyState
            title="No workflows yet"
            description="Create the first workflow to connect OCR events with notifications, audit records, and Knowledge placeholders."
          />
        ) : state === "error" ? (
          <ErrorState
            title="Flows could not load"
            description="The Flows API did not return workflow definitions or run history for this workspace."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
                onClick={() => {
                  workflowsQuery.refetch();
                  executionsQuery.refetch();
                }}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Retry Flows
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Flows access is not available"
            description="The current workspace role cannot view or execute workflows."
          />
        ) : (
          <FlowsNormalState
            workflows={workflows}
            executions={executions}
            selectedWorkflowSummary={selectedWorkflowSummary}
            selectedWorkflow={selectedWorkflow}
            selectedExecution={selectedExecution}
            runError={runMutation.isError}
            onSelectWorkflow={(workflow) =>
              setSelectedWorkflowId(workflow.workflowId)
            }
            onSelectExecution={(execution) => {
              setSelectedExecutionId(execution.executionId);
              setMobileSheetOpen(true);
            }}
          />
        )}
      </div>

      <MobileBottomSheet
        title={selectedExecution?.workflowName ?? "Workflow execution"}
        open={
          state === "normal" && mobileSheetOpen && selectedExecution !== null
        }
        onClose={() => setMobileSheetOpen(false)}
      >
        {selectedExecution ? (
          <FlowExecutionDetails
            execution={selectedExecution}
            detailLoading={detailLoading}
          />
        ) : null}
      </MobileBottomSheet>
    </AppShell>
  );
}
