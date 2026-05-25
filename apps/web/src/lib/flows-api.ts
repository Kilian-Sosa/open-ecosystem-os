import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

export type JsonObject = { [key: string]: JsonValue };

export type WorkflowStatus = "draft" | "active" | "paused";
export type WorkflowTriggerType = "manual" | "event";
export type WorkflowExecutionStatus = "running" | "completed" | "failed";
export type WorkflowActionType =
  | "create_notification"
  | "create_audit_entry"
  | "create_knowledge_item_placeholder";

export type WorkflowTrigger =
  | { type: "manual" }
  | { type: "event"; eventType: "OcrCompleted" };

export type WorkflowAction =
  | {
      type: "create_notification";
      title?: string;
      body?: string;
      severity?: "info" | "warning" | "danger";
    }
  | {
      type: "create_audit_entry";
      action?: string;
      resourceType?: string;
      attributes?: Record<string, string>;
    }
  | {
      type: "create_knowledge_item_placeholder";
      title?: string;
      summary?: string;
    };

export type WorkflowStepDefinition = {
  id: string;
  name: string;
  action: WorkflowAction;
};

export type WorkflowDefinition = {
  trigger: WorkflowTrigger;
  steps: WorkflowStepDefinition[];
};

export type WorkflowSummary = {
  workflowId: string;
  name: string;
  description: string;
  status: WorkflowStatus;
  currentVersionNumber: number;
  triggerType: WorkflowTriggerType;
  triggerEventType: string | null;
  stepCount: number;
  updatedAt: string;
};

export type WorkflowDetail = {
  workflowId: string;
  name: string;
  description: string;
  status: WorkflowStatus;
  currentVersionId: string;
  currentVersionNumber: number;
  definition: WorkflowDefinition;
  createdAt: string;
  updatedAt: string;
};

export type WorkflowListResponse = {
  workflows: WorkflowSummary[];
};

export type WorkflowStepExecution = {
  stepExecutionId: string;
  stepKey: string;
  stepName: string;
  actionType: WorkflowActionType;
  status: WorkflowExecutionStatus;
  retryCount: number;
  failureReason: string | null;
  input: JsonObject;
  output: JsonObject;
  startedAt: string;
  completedAt: string | null;
  failedAt: string | null;
  updatedAt: string;
};

export type WorkflowExecutionSummary = {
  executionId: string;
  workflowId: string;
  workflowName: string;
  workflowVersionNumber: number;
  triggerType: WorkflowTriggerType;
  sourceEventType: string | null;
  sourceEventId: string | null;
  status: WorkflowExecutionStatus;
  retryCount: number;
  failureReason: string | null;
  correlationId: string;
  startedAt: string;
  completedAt: string | null;
  failedAt: string | null;
  updatedAt: string;
};

export type WorkflowExecutionDetail = WorkflowExecutionSummary & {
  steps: WorkflowStepExecution[];
};

export type WorkflowExecutionListResponse = {
  executions: WorkflowExecutionSummary[];
};

export async function fetchWorkflows(): Promise<WorkflowListResponse> {
  const response = await fetch(`${API_BASE_URL}/api/flows/workflows`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new Error("Workflows could not be loaded");
  }

  return response.json() as Promise<WorkflowListResponse>;
}

export async function fetchWorkflow(
  workflowId: string,
): Promise<WorkflowDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/flows/workflows/${workflowId}`,
    {
      headers: workspaceHeaders,
    },
  );

  if (!response.ok) {
    throw new Error("Workflow could not be loaded");
  }

  return response.json() as Promise<WorkflowDetail>;
}

export async function runWorkflow(
  workflowId: string,
): Promise<WorkflowExecutionDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/flows/workflows/${workflowId}/runs`,
    {
      method: "POST",
      headers: workspaceHeaders,
    },
  );

  if (!response.ok) {
    throw new Error("Workflow could not be run");
  }

  return response.json() as Promise<WorkflowExecutionDetail>;
}

export async function fetchWorkflowExecutions(): Promise<WorkflowExecutionListResponse> {
  const response = await fetch(`${API_BASE_URL}/api/flows/executions`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new Error("Workflow executions could not be loaded");
  }

  return response.json() as Promise<WorkflowExecutionListResponse>;
}

export async function fetchWorkflowExecution(
  executionId: string,
): Promise<WorkflowExecutionDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/flows/executions/${executionId}`,
    {
      headers: workspaceHeaders,
    },
  );

  if (!response.ok) {
    throw new Error("Workflow execution could not be loaded");
  }

  return response.json() as Promise<WorkflowExecutionDetail>;
}
