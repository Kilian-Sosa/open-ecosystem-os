import type {
  WorkflowDetail,
  WorkflowExecutionDetail,
  WorkflowExecutionSummary,
  WorkflowSummary,
} from "@/lib/flows-api";

export type FlowsState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

export function parseFlowsState(
  value: string | string[] | undefined,
): FlowsState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  if (
    state === "normal" ||
    state === "loading" ||
    state === "empty" ||
    state === "error" ||
    state === "permission-denied"
  ) {
    return state;
  }
  return undefined;
}

export const flowsMockWorkflows: WorkflowSummary[] = [
  {
    workflowId: "flow_invoice_automation",
    name: "Invoice Processing Automation",
    description:
      "Runs when OCR completes and creates the first notification, audit entry, and Knowledge placeholder.",
    status: "active",
    currentVersionNumber: 1,
    triggerType: "event",
    triggerEventType: "OcrCompleted",
    stepCount: 3,
    updatedAt: "2026-05-23T08:00:00Z",
  },
  {
    workflowId: "flow_manual_review",
    name: "Manual Review Smoke Test",
    description: "Runs the same vertical action chain from the Run button.",
    status: "draft",
    currentVersionNumber: 2,
    triggerType: "manual",
    triggerEventType: null,
    stepCount: 2,
    updatedAt: "2026-05-22T12:30:00Z",
  },
];

export const flowsMockWorkflowDetails: WorkflowDetail[] = [
  {
    workflowId: "flow_invoice_automation",
    name: "Invoice Processing Automation",
    description:
      "Runs when OCR completes and creates the first notification, audit entry, and Knowledge placeholder.",
    status: "active",
    currentVersionId: "wfv_invoice_automation_v1",
    currentVersionNumber: 1,
    createdAt: "2026-05-23T08:00:00Z",
    updatedAt: "2026-05-23T08:00:00Z",
    definition: {
      trigger: { type: "event", eventType: "OcrCompleted" },
      steps: [
        {
          id: "notify-review",
          name: "Create review notification",
          action: {
            type: "create_notification",
            title: "OCR completed for invoice file",
            body: "A document finished OCR and is ready for review.",
            severity: "info",
          },
        },
        {
          id: "audit-automation",
          name: "Record automation audit",
          action: {
            type: "create_audit_entry",
            action: "flows.invoice_automation.completed",
            resourceType: "workflow_execution",
            attributes: { workflow: "invoice_automation" },
          },
        },
        {
          id: "knowledge-placeholder",
          name: "Create knowledge placeholder",
          action: {
            type: "create_knowledge_item_placeholder",
            title: "OCR knowledge placeholder",
            summary:
              "A placeholder Knowledge item was created from an OCR completion event.",
          },
        },
      ],
    },
  },
  {
    workflowId: "flow_manual_review",
    name: "Manual Review Smoke Test",
    description: "Runs the same vertical action chain from the Run button.",
    status: "draft",
    currentVersionId: "wfv_manual_review_v2",
    currentVersionNumber: 2,
    createdAt: "2026-05-21T08:00:00Z",
    updatedAt: "2026-05-22T12:30:00Z",
    definition: {
      trigger: { type: "manual" },
      steps: [
        {
          id: "notify-review",
          name: "Create review notification",
          action: {
            type: "create_notification",
            title: "Manual workflow completed",
            body: "The manual smoke test completed.",
            severity: "info",
          },
        },
        {
          id: "audit-automation",
          name: "Record automation audit",
          action: {
            type: "create_audit_entry",
            action: "flows.manual_review.completed",
          },
        },
      ],
    },
  },
];

export const flowsMockExecutions: WorkflowExecutionSummary[] = [
  {
    executionId: "wfe_success",
    workflowId: "flow_invoice_automation",
    workflowName: "Invoice Processing Automation",
    workflowVersionNumber: 1,
    triggerType: "event",
    sourceEventType: "OcrCompleted",
    sourceEventId: "evt_ocr_completed",
    status: "completed",
    retryCount: 0,
    failureReason: null,
    correlationId: "corr_invoice_001",
    startedAt: "2026-05-23T08:02:00Z",
    completedAt: "2026-05-23T08:02:03Z",
    failedAt: null,
    updatedAt: "2026-05-23T08:02:03Z",
  },
  {
    executionId: "wfe_failed",
    workflowId: "flow_invoice_automation",
    workflowName: "Invoice Processing Automation",
    workflowVersionNumber: 1,
    triggerType: "event",
    sourceEventType: "OcrCompleted",
    sourceEventId: "evt_ocr_completed_failed",
    status: "failed",
    retryCount: 1,
    failureReason: "Notification title is required",
    correlationId: "corr_invoice_002",
    startedAt: "2026-05-23T07:45:00Z",
    completedAt: null,
    failedAt: "2026-05-23T07:45:01Z",
    updatedAt: "2026-05-23T07:45:01Z",
  },
];

export const flowsMockExecutionDetails: WorkflowExecutionDetail[] = [
  {
    ...flowsMockExecutions[0],
    steps: [
      {
        stepExecutionId: "wfs_notify",
        stepKey: "notify-review",
        stepName: "Create review notification",
        actionType: "create_notification",
        status: "completed",
        retryCount: 0,
        failureReason: null,
        input: {
          triggerType: "event",
          sourceEventId: "evt_ocr_completed",
          sourceEventType: "OcrCompleted",
          ocrJobId: "ocr_123",
          fileId: "file_123",
          extractedTextLength: 2048,
        },
        output: { notificationId: "ntf_123", severity: "info" },
        startedAt: "2026-05-23T08:02:00Z",
        completedAt: "2026-05-23T08:02:01Z",
        failedAt: null,
        updatedAt: "2026-05-23T08:02:01Z",
      },
      {
        stepExecutionId: "wfs_audit",
        stepKey: "audit-automation",
        stepName: "Record automation audit",
        actionType: "create_audit_entry",
        status: "completed",
        retryCount: 0,
        failureReason: null,
        input: { triggerType: "event", sourceEventId: "evt_ocr_completed" },
        output: {
          auditId: "aud_123",
          action: "flows.invoice_automation.completed",
        },
        startedAt: "2026-05-23T08:02:01Z",
        completedAt: "2026-05-23T08:02:02Z",
        failedAt: null,
        updatedAt: "2026-05-23T08:02:02Z",
      },
      {
        stepExecutionId: "wfs_knowledge",
        stepKey: "knowledge-placeholder",
        stepName: "Create knowledge placeholder",
        actionType: "create_knowledge_item_placeholder",
        status: "completed",
        retryCount: 0,
        failureReason: null,
        input: { triggerType: "event", sourceEventId: "evt_ocr_completed" },
        output: { knowledgeItemId: "knw_123" },
        startedAt: "2026-05-23T08:02:02Z",
        completedAt: "2026-05-23T08:02:03Z",
        failedAt: null,
        updatedAt: "2026-05-23T08:02:03Z",
      },
    ],
  },
  {
    ...flowsMockExecutions[1],
    steps: [
      {
        stepExecutionId: "wfs_failed",
        stepKey: "notify-review",
        stepName: "Create review notification",
        actionType: "create_notification",
        status: "failed",
        retryCount: 1,
        failureReason: "Notification title is required",
        input: {
          triggerType: "event",
          sourceEventId: "evt_ocr_completed_failed",
        },
        output: {},
        startedAt: "2026-05-23T07:45:00Z",
        completedAt: null,
        failedAt: "2026-05-23T07:45:01Z",
        updatedAt: "2026-05-23T07:45:01Z",
      },
    ],
  },
];
