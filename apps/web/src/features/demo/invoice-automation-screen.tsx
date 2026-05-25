"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bell,
  CheckCircle2,
  Clock3,
  Database,
  ExternalLink,
  FileText,
  RotateCcw,
  Search,
  ShieldCheck,
  UploadCloud,
  Workflow,
  XCircle,
} from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { AppShell } from "@/components/layout/app-shell";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PermissionDeniedState,
  SectionCard,
  StatusChip,
  type StatusKind,
} from "@/components/ui";
import type {
  DemoInvoiceExtraction,
  DemoInvoiceLinks,
  DemoInvoiceRun,
  DemoTimelineStatus,
  DemoTimelineStep,
} from "@/lib/demo-invoice-api";
import {
  fetchDemoInvoiceRun,
  resetDemoInvoiceRuns,
  startDemoInvoiceRun,
} from "@/lib/demo-invoice-api";
import { cn } from "@/lib/cn";

export type DemoInvoiceState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

type DemoInvoiceAutomationScreenProps = {
  stateOverride?: DemoInvoiceState;
};

const demoInvoiceRunQueryKey = ["demo", "invoice-automation", "run"];

const demoInvoiceMockRun: DemoInvoiceRun = {
  runId: "demo_mock_invoice_run",
  correlationId: "corr_demo_invoice_mock",
  fileId: "file_demo_invoice_mock",
  ocrJobId: "ocr_demo_invoice_mock",
  workflowExecutionId: "wfe_demo_invoice_mock",
  notificationId: "ntf_demo_invoice_mock",
  searchDocumentId: "srch_demo_invoice_mock",
  status: "completed",
  links: {
    drive: "/app/drive?fileId=file_demo_invoice_mock",
    ocr: "/app/media?jobId=ocr_demo_invoice_mock",
    flows: "/app/flows?executionId=wfe_demo_invoice_mock",
    notifications: "/app/notifications?correlationId=corr_demo_invoice_mock",
    audit: "/admin/audit?correlationId=corr_demo_invoice_mock",
    search: "/app/search?q=TEST-INV-2026-0001",
  },
  timeline: [
    {
      key: "drive",
      label: "Drive upload",
      status: "completed",
      detail: "Fake/test invoice placeholder stored in Drive.",
      href: "/app/drive?fileId=file_demo_invoice_mock",
      occurredAt: "2026-05-25T09:00:00Z",
    },
    {
      key: "ocr",
      label: "Mock OCR",
      status: "completed",
      detail: "OCR job ocr_demo_invoice_mock is completed.",
      href: "/app/media?jobId=ocr_demo_invoice_mock",
      occurredAt: "2026-05-25T09:00:05Z",
    },
    {
      key: "flows",
      label: "Automation workflow",
      status: "completed",
      detail: "Workflow execution wfe_demo_invoice_mock is completed.",
      href: "/app/flows?executionId=wfe_demo_invoice_mock",
      occurredAt: "2026-05-25T09:00:09Z",
    },
    {
      key: "notification",
      label: "Notification",
      status: "completed",
      detail: "Notification ntf_demo_invoice_mock created.",
      href: "/app/notifications?correlationId=corr_demo_invoice_mock",
      occurredAt: "2026-05-25T09:00:10Z",
    },
    {
      key: "audit",
      label: "Audit records",
      status: "completed",
      detail: "Workflow audit record aud_demo_invoice_mock created.",
      href: "/admin/audit?correlationId=corr_demo_invoice_mock",
      occurredAt: "2026-05-25T09:00:10Z",
    },
    {
      key: "search",
      label: "Search indexing",
      status: "completed",
      detail: "Search document srch_demo_invoice_mock is indexed.",
      href: "/app/search?q=TEST-INV-2026-0001",
      occurredAt: "2026-05-25T09:00:13Z",
    },
  ],
  extraction: {
    extractionId: "dinv_demo_invoice_mock",
    invoiceNumber: "TEST-INV-2026-0001",
    supplierName: "Demo Supplies S.L. (fake/test data)",
    supplierTestNif: "B00000000 (test data)",
    supplierTestIban: "ES00 0000 0000 0000 0000 0000 (test data)",
    totalAmount: 124,
    currency: "EUR",
    dueDate: "2026-06-15",
    isTestData: true,
  },
  createdAt: "2026-05-25T09:00:00Z",
  updatedAt: "2026-05-25T09:00:13Z",
};

export function InvoiceAutomationScreen({
  stateOverride,
}: DemoInvoiceAutomationScreenProps) {
  const queryClient = useQueryClient();
  const [runId, setRunId] = useState<string | null>(null);
  const startMutation = useMutation({
    mutationFn: startDemoInvoiceRun,
    onSuccess: (run) => {
      setRunId(run.runId);
      queryClient.setQueryData([...demoInvoiceRunQueryKey, run.runId], run);
    },
  });
  const resetMutation = useMutation({
    mutationFn: resetDemoInvoiceRuns,
    onSuccess: () => {
      setRunId(null);
      queryClient.removeQueries({ queryKey: demoInvoiceRunQueryKey });
    },
  });
  const runQuery = useQuery({
    queryKey: [...demoInvoiceRunQueryKey, runId],
    queryFn: () => fetchDemoInvoiceRun(runId ?? ""),
    enabled: stateOverride === undefined && runId !== null,
    refetchInterval: (query) =>
      query.state.data?.status === "processing" ? 1500 : false,
  });

  const run =
    stateOverride === "normal"
      ? demoInvoiceMockRun
      : (runQuery.data ?? startMutation.data ?? null);
  const state = resolveState(
    stateOverride,
    runId !== null && run === null && runQuery.isPending,
    startMutation.isError || resetMutation.isError || runQuery.isError,
    run,
  );

  return (
    <AppShell activeHref="/app/demo/invoice-automation">
      <div className="space-y-6">
        <PageHeader
          title="Invoice automation demo"
          subtitle="Run the seeded Drive to OCR to Flows to notification, audit, and search path with fake/test invoice data."
          chips={
            <>
              <StatusChip status="active" label="Flagship demo" />
              <StatusChip status="queued" label="Fake/test data only" />
            </>
          }
          primaryAction={
            <button
              type="button"
              className="inline-flex min-h-10 items-center gap-2 rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
              disabled={startMutation.isPending}
              onClick={() => startMutation.mutate()}
            >
              <FileText className="h-4 w-4" aria-hidden="true" />
              {startMutation.isPending ? "Starting..." : "Run demo"}
            </button>
          }
          secondaryAction={
            <button
              type="button"
              className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-60"
              disabled={resetMutation.isPending}
              onClick={() => resetMutation.mutate()}
            >
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              {resetMutation.isPending ? "Resetting..." : "Reset demo data"}
            </button>
          }
        />

        {resetMutation.isSuccess && state === "empty" ? (
          <div className="rounded-card border border-success-soft bg-success-soft p-4 text-sm text-success">
            Removed {resetMutation.data.runsDeleted} demo runs and{" "}
            {resetMutation.data.objectsDeleted} demo objects.
          </div>
        ) : null}

        {state === "loading" ? (
          <LoadingState label="Loading invoice automation demo" />
        ) : state === "empty" ? (
          <EmptyState
            title="No invoice demo run yet"
            description="Start a seeded run to create the fake/test invoice and follow the vertical automation path."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover"
                onClick={() => startMutation.mutate()}
              >
                <FileText className="h-4 w-4" aria-hidden="true" />
                Run demo
              </button>
            }
          />
        ) : state === "error" ? (
          <ErrorState
            title="Invoice demo could not load"
            description="The demo API did not return the run status. Check the API and worker services, then retry."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
                onClick={() =>
                  runId ? runQuery.refetch() : startMutation.mutate()
                }
              >
                <RotateCcw className="h-4 w-4" aria-hidden="true" />
                Retry
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Demo access is not available"
            description="The current workspace role cannot start seeded invoice automation demos."
          />
        ) : run ? (
          <DemoRunView run={run} />
        ) : null}
      </div>
    </AppShell>
  );
}

function resolveState(
  override: DemoInvoiceState | undefined,
  loading: boolean,
  error: boolean,
  run: DemoInvoiceRun | null,
): DemoInvoiceState {
  if (override) return override;
  if (loading) return "loading";
  if (error) return "error";
  return run === null ? "empty" : "normal";
}

function DemoRunView({ run }: { run: DemoInvoiceRun }) {
  const searchMetric = searchMetricState(run);

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <DemoMetric
          label="Run"
          value={run.status}
          detail={run.runId}
          status={statusChip(run.status)}
        />
        <DemoMetric
          label="Correlation"
          value="Trace"
          detail={run.correlationId}
          status="active"
        />
        <DemoMetric
          label="Search"
          value={searchMetric.value}
          detail={run.searchDocumentId ?? "Waiting for indexing request"}
          status={searchMetric.status}
        />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <SectionCard
          title="Progress timeline"
          description="Status is derived from the seeded records created by the backend and workers."
          action={
            <StatusChip
              status={statusChip(run.status)}
              label={formatStatusLabel(run.status)}
            />
          }
        >
          <div className="space-y-4">
            {run.timeline.map((step) => (
              <TimelineStep key={step.key} step={step} />
            ))}
          </div>
        </SectionCard>

        <div className="space-y-6">
          <InvoiceExtractionCard extraction={run.extraction} />
          <AppLinksCard links={run.links} />
        </div>
      </div>
    </div>
  );
}

function DemoMetric({
  label,
  value,
  detail,
  status,
}: {
  label: string;
  value: string;
  detail: string;
  status: StatusKind;
}) {
  return (
    <div className="rounded-card border border-border bg-surface p-4 shadow-card">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-normal text-text-muted">
            {label}
          </p>
          <p className="mt-2 truncate text-2xl font-semibold capitalize text-text-primary">
            {value}
          </p>
        </div>
        <StatusChip
          status={status}
          label={status === "draft" ? "Pending" : undefined}
        />
      </div>
      <p className="mt-2 truncate text-sm text-text-secondary">{detail}</p>
    </div>
  );
}

function TimelineStep({ step }: { step: DemoTimelineStep }) {
  return (
    <div className="flex gap-4">
      <div
        className={cn(
          "mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-card",
          step.status === "completed" && "bg-success-soft text-success",
          (step.status === "processing" || step.status === "running") &&
            "bg-info-soft text-info",
          step.status === "pending" && "bg-warning-soft text-warning",
          step.status === "failed" && "bg-danger-soft text-danger",
        )}
      >
        <TimelineStatusIcon status={step.status} />
      </div>
      <div className="min-w-0 flex-1 rounded-card border border-border bg-surface p-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-text-primary">
              {step.label}
            </p>
            <p className="mt-1 text-sm leading-5 text-text-secondary">
              {step.detail}
            </p>
            <p className="mt-2 text-xs text-text-muted">
              {step.occurredAt ? formatDate(step.occurredAt) : "Not recorded"}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <StatusChip
              status={statusChip(step.status)}
              label={formatStatusLabel(step.status)}
            />
            <Link
              href={step.href}
              className="inline-flex h-8 w-8 items-center justify-center rounded-card border border-border text-text-secondary hover:bg-surface-muted hover:text-text-primary"
              aria-label={`Open ${step.label}`}
            >
              <ExternalLink className="h-4 w-4" aria-hidden="true" />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function TimelineStatusIcon({ status }: { status: DemoTimelineStatus }) {
  if (status === "completed")
    return <CheckCircle2 className="h-4 w-4" aria-hidden="true" />;
  if (status === "failed")
    return <XCircle className="h-4 w-4" aria-hidden="true" />;
  if (status === "pending")
    return <Clock3 className="h-4 w-4" aria-hidden="true" />;
  return <Database className="h-4 w-4" aria-hidden="true" />;
}

function InvoiceExtractionCard({
  extraction,
}: {
  extraction: DemoInvoiceExtraction | null;
}) {
  return (
    <SectionCard
      title="Extracted invoice fields"
      description="The extractor is deterministic and every value is fake/test data."
      action={<StatusChip status={extraction ? "completed" : "queued"} />}
    >
      {extraction ? (
        <dl className="space-y-3 text-sm">
          <DetailRow label="Invoice number" value={extraction.invoiceNumber} />
          <DetailRow label="Supplier" value={extraction.supplierName} />
          <DetailRow
            label="Total"
            value={formatMoney(extraction.totalAmount, extraction.currency)}
          />
          <DetailRow label="Due date" value={extraction.dueDate} />
          {extraction.supplierTestNif ? (
            <DetailRow label="Test NIF" value={extraction.supplierTestNif} />
          ) : null}
          {extraction.supplierTestIban ? (
            <DetailRow label="Test IBAN" value={extraction.supplierTestIban} />
          ) : null}
          <DetailRow
            label="Data marker"
            value={extraction.isTestData ? "Fake/test data" : "Not marked"}
          />
        </dl>
      ) : (
        <p className="text-sm leading-6 text-text-secondary">
          Extraction appears after OCR completes and the workflow action runs.
        </p>
      )}
    </SectionCard>
  );
}

function AppLinksCard({ links }: { links: DemoInvoiceLinks }) {
  const items = [
    { label: "Drive", href: links.drive, icon: FileText },
    { label: "OCR", href: links.ocr, icon: UploadCloud },
    { label: "Flows", href: links.flows, icon: Workflow },
    { label: "Notifications", href: links.notifications, icon: Bell },
    { label: "Audit", href: links.audit, icon: ShieldCheck },
    { label: "Search", href: links.search, icon: Search },
  ];

  return (
    <SectionCard title="Linked workspace apps">
      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.label}
              href={item.href}
              className="flex min-h-11 items-center justify-between gap-3 rounded-card border border-border bg-surface px-3 text-sm font-medium text-text-primary hover:bg-surface-muted"
            >
              <span className="flex min-w-0 items-center gap-3">
                <Icon
                  className="h-4 w-4 shrink-0 text-text-secondary"
                  aria-hidden="true"
                />
                <span className="truncate">{item.label}</span>
              </span>
              <ExternalLink
                className="h-4 w-4 shrink-0 text-text-muted"
                aria-hidden="true"
              />
            </Link>
          );
        })}
      </div>
    </SectionCard>
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

function statusChip(status: DemoTimelineStatus): StatusKind {
  if (status === "completed") return "completed";
  if (status === "failed") return "failed";
  if (status === "pending") return "queued";
  return "processing";
}

function formatStatusLabel(status: DemoTimelineStatus) {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

function searchMetricState(run: DemoInvoiceRun): {
  status: StatusKind;
  value: string;
} {
  const searchStep = run.timeline.find((step) => step.key === "search");
  if (!run.searchDocumentId || searchStep?.status === "pending")
    return { status: "draft", value: "Pending" };
  if (searchStep?.status === "completed")
    return { status: "completed", value: "Indexed" };
  if (searchStep?.status === "failed")
    return { status: "failed", value: "Failed" };
  return { status: "processing", value: "Requested" };
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat("en", {
    style: "currency",
    currency,
  }).format(amount);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
