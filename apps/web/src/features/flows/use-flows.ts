"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  fetchWorkflow,
  fetchWorkflowExecution,
  fetchWorkflowExecutions,
  fetchWorkflows,
  runWorkflow,
  type WorkflowDetail,
  type WorkflowExecutionDetail,
  type WorkflowExecutionListResponse,
  type WorkflowListResponse,
} from "@/lib/flows-api";

const workflowsQueryKey = ["flows", "workflows"];
const workflowExecutionsQueryKey = ["flows", "executions"];

export function useWorkflows(enabled = true) {
  return useQuery<WorkflowListResponse>({
    queryKey: workflowsQueryKey,
    queryFn: fetchWorkflows,
    enabled,
  });
}

export function useWorkflow(workflowId: string | null, enabled = true) {
  return useQuery<WorkflowDetail>({
    queryKey: [...workflowsQueryKey, workflowId],
    queryFn: () => fetchWorkflow(workflowId ?? ""),
    enabled: enabled && workflowId !== null,
  });
}

export function useWorkflowExecutions(enabled = true) {
  return useQuery<WorkflowExecutionListResponse>({
    queryKey: workflowExecutionsQueryKey,
    queryFn: fetchWorkflowExecutions,
    enabled,
  });
}

export function useWorkflowExecution(
  executionId: string | null,
  enabled = true,
) {
  return useQuery<WorkflowExecutionDetail>({
    queryKey: [...workflowExecutionsQueryKey, executionId],
    queryFn: () => fetchWorkflowExecution(executionId ?? ""),
    enabled: enabled && executionId !== null,
  });
}

export function useRunWorkflow(
  onRun?: (execution: WorkflowExecutionDetail) => void,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: runWorkflow,
    onSuccess: (execution) => {
      queryClient.invalidateQueries({ queryKey: workflowExecutionsQueryKey });
      onRun?.(execution);
    },
  });
}
