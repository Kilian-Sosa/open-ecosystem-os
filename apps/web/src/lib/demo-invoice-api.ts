import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type DemoTimelineStatus =
  | "pending"
  | "processing"
  | "running"
  | "completed"
  | "failed";

export type DemoTimelineStep = {
  key: string;
  label: string;
  status: DemoTimelineStatus;
  detail: string;
  href: string;
  occurredAt: string | null;
};

export type DemoInvoiceLinks = {
  drive: string;
  ocr: string;
  flows: string;
  notifications: string;
  audit: string;
  search: string;
};

export type DemoInvoiceExtraction = {
  extractionId: string;
  invoiceNumber: string;
  supplierName: string;
  supplierTestNif: string | null;
  supplierTestIban: string | null;
  totalAmount: number;
  currency: string;
  dueDate: string;
  isTestData: boolean;
};

export type DemoInvoiceRun = {
  runId: string;
  correlationId: string;
  fileId: string;
  ocrJobId: string | null;
  workflowExecutionId: string | null;
  notificationId: string | null;
  searchDocumentId: string | null;
  status: DemoTimelineStatus;
  links: DemoInvoiceLinks;
  timeline: DemoTimelineStep[];
  extraction: DemoInvoiceExtraction | null;
  createdAt: string;
  updatedAt: string;
};

export type DemoInvoiceResetResponse = {
  runsDeleted: number;
  objectsDeleted: number;
};

export async function startDemoInvoiceRun(): Promise<DemoInvoiceRun> {
  const response = await fetch(
    `${API_BASE_URL}/api/demo/invoice-automation/runs`,
    {
      method: "POST",
      headers: workspaceHeaders,
    },
  );

  if (!response.ok) {
    throw new Error("Demo invoice run could not be started");
  }

  return response.json() as Promise<DemoInvoiceRun>;
}

export async function fetchDemoInvoiceRun(
  runId: string,
): Promise<DemoInvoiceRun> {
  const response = await fetch(
    `${API_BASE_URL}/api/demo/invoice-automation/runs/${runId}`,
    {
      headers: workspaceHeaders,
    },
  );

  if (!response.ok) {
    throw new Error("Demo invoice run could not be loaded");
  }

  return response.json() as Promise<DemoInvoiceRun>;
}

export async function resetDemoInvoiceRuns(): Promise<DemoInvoiceResetResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/demo/invoice-automation/reset`,
    {
      method: "POST",
      headers: workspaceHeaders,
    },
  );

  if (!response.ok) {
    throw new Error("Demo invoice data could not be reset");
  }

  return response.json() as Promise<DemoInvoiceResetResponse>;
}
