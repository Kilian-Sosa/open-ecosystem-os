import type { StatusKind } from "@/components/ui/status-chip";

export type DashboardState = "normal" | "loading" | "empty" | "error";
export type IconToken =
  | "activity"
  | "automation"
  | "database"
  | "document"
  | "file"
  | "health"
  | "notification"
  | "ocr"
  | "search"
  | "storage"
  | "upload"
  | "workflow";

export type DashboardMetric = {
  id: string;
  label: string;
  value: string;
  detail: string;
  trend?: string;
  progress?: number;
  icon: IconToken;
  tone: "primary" | "success" | "warning" | "danger" | "info";
};

export type RecentFile = {
  id: string;
  name: string;
  location: string;
  updatedAt: string;
  type: "pdf" | "figma" | "document" | "markdown";
};

export type AutomationSummary = {
  id: string;
  name: string;
  trigger: string;
  status: StatusKind;
};

export type ProcessingJob = {
  id: string;
  name: string;
  detail: string;
  status: StatusKind;
  progress: number;
};

export type ActivityEntry = {
  id: string;
  title: string;
  meta: string;
  time: string;
  icon: IconToken;
};

export type QuickAction = {
  id: string;
  title: string;
  description: string;
  icon: IconToken;
  tone: "primary" | "success" | "warning" | "info";
};

export type SystemService = {
  id: string;
  name: string;
  status: StatusKind;
  detail: string;
};

export type DashboardMockData = {
  metrics: DashboardMetric[];
  recentFiles: RecentFile[];
  automations: AutomationSummary[];
  processingJobs: ProcessingJob[];
  activity: ActivityEntry[];
  quickActions: QuickAction[];
  services: SystemService[];
  storage: {
    usedLabel: string;
    totalLabel: string;
    percentUsed: number;
  };
};

export const dashboardMockData: DashboardMockData = {
  metrics: [
    {
      id: "files",
      label: "Files",
      value: "12,458",
      trend: "+8%",
      detail: "this week",
      icon: "file",
      tone: "primary",
    },
    {
      id: "storage",
      label: "Storage used",
      value: "860 GB",
      detail: "of 2 TB",
      progress: 42,
      icon: "storage",
      tone: "info",
    },
    {
      id: "automations",
      label: "Automations",
      value: "18",
      detail: "12 active",
      icon: "automation",
      tone: "warning",
    },
    {
      id: "processing",
      label: "Processing jobs",
      value: "7",
      detail: "3 running",
      icon: "ocr",
      tone: "success",
    },
  ],
  recentFiles: [
    {
      id: "file-1",
      name: "Project Proposal.pdf",
      location: "Drive / Proposals",
      updatedAt: "2m ago",
      type: "pdf",
    },
    {
      id: "file-2",
      name: "Design System.fig",
      location: "Drive / Design",
      updatedAt: "1h ago",
      type: "figma",
    },
    {
      id: "file-3",
      name: "Meeting Notes.docx",
      location: "Docs",
      updatedAt: "3h ago",
      type: "document",
    },
    {
      id: "file-4",
      name: "Invoice_2026-05.pdf",
      location: "Drive / Finance",
      updatedAt: "Yesterday",
      type: "pdf",
    },
  ],
  automations: [
    {
      id: "flow-1",
      name: "PDF OCR Processing",
      trigger: "When a file is uploaded",
      status: "running",
    },
    {
      id: "flow-2",
      name: "Invoice Parser",
      trigger: "Every time a PDF is added",
      status: "running",
    },
    {
      id: "flow-3",
      name: "Daily Backup",
      trigger: "Every day at 02:00",
      status: "scheduled",
    },
  ],
  processingJobs: [
    {
      id: "job-1",
      name: "OCR - scans_001.pdf",
      detail: "Extracting text",
      status: "processing",
      progress: 72,
    },
    {
      id: "job-2",
      name: "Thumbnail Generation",
      detail: "Generating previews",
      status: "processing",
      progress: 45,
    },
    {
      id: "job-3",
      name: "Audio Transcription",
      detail: "Converted to text",
      status: "completed",
      progress: 100,
    },
  ],
  activity: [
    {
      id: "activity-1",
      title: "Uploaded 3 files to Drive/Proposals",
      meta: "Correlation corr_drive_upload_1024",
      time: "2m ago",
      icon: "upload",
    },
    {
      id: "activity-2",
      title: "Automation PDF OCR Processing completed",
      meta: "OcrCompleted event consumed",
      time: "15m ago",
      icon: "workflow",
    },
    {
      id: "activity-3",
      title: "New review notification created",
      meta: "Invoice extraction ready for approval",
      time: "1h ago",
      icon: "notification",
    },
    {
      id: "activity-4",
      title: "Admin signed in",
      meta: "Audit log recorded",
      time: "2h ago",
      icon: "activity",
    },
  ],
  quickActions: [
    {
      id: "upload",
      title: "Upload invoice",
      description: "Start the Drive to OCR workflow.",
      icon: "upload",
      tone: "primary",
    },
    {
      id: "new-page",
      title: "New document",
      description: "Capture notes or extracted details.",
      icon: "document",
      tone: "success",
    },
    {
      id: "new-flow",
      title: "Create automation",
      description: "Draft a workspace workflow.",
      icon: "automation",
      tone: "warning",
    },
    {
      id: "search",
      title: "Search evidence",
      description: "Find files, OCR text, and activity.",
      icon: "search",
      tone: "info",
    },
  ],
  services: [
    {
      id: "api",
      name: "API Gateway",
      status: "healthy",
      detail: "99.99% uptime",
    },
    {
      id: "database",
      name: "Database",
      status: "healthy",
      detail: "12 ms p95",
    },
    {
      id: "storage",
      name: "Object Storage",
      status: "healthy",
      detail: "860 GB used",
    },
    {
      id: "queue",
      name: "Redis Queue",
      status: "healthy",
      detail: "7 jobs active",
    },
    {
      id: "worker",
      name: "OCR Workers",
      status: "running",
      detail: "3 running",
    },
  ],
  storage: {
    usedLabel: "860 GB",
    totalLabel: "2 TB",
    percentUsed: 42,
  },
};

export function parseDashboardState(
  value: string | string[] | undefined,
): DashboardState {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw === "loading" || raw === "empty" || raw === "error") {
    return raw;
  }
  return "normal";
}
