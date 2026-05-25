"use client";

import { useQuery } from "@tanstack/react-query";
import { Activity, RefreshCw, ShieldCheck } from "lucide-react";

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
import type { AuditRecord } from "@/lib/audit-api";
import { fetchAuditRecords } from "@/lib/audit-api";

export type AuditLogState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

type AuditLogScreenProps = {
  correlationId?: string;
  stateOverride?: AuditLogState;
};

const mockAuditRecords: AuditRecord[] = [
  {
    auditId: "aud_demo_invoice_mock",
    action: "flows.demo_invoice.extracted",
    resourceType: "demo_invoice_extraction",
    resourceId: "dinv_demo_invoice_mock",
    actorId: "usr_dev_placeholder",
    correlationId: "corr_demo_invoice_mock",
    outcome: "success",
    attributes: {
      invoiceNumber: "TEST-INV-2026-0001",
      isTestData: "true",
    },
    occurredAt: "2026-05-25T09:00:10Z",
  },
];

export function AuditLogScreen({
  correlationId,
  stateOverride,
}: AuditLogScreenProps) {
  const auditQuery = useQuery({
    queryKey: ["audit-records", correlationId ?? ""],
    queryFn: () => fetchAuditRecords(correlationId),
    enabled: stateOverride === undefined,
  });
  const records =
    stateOverride === "normal"
      ? mockAuditRecords
      : (auditQuery.data?.records ?? []);
  const state = resolveState(
    stateOverride,
    auditQuery.isPending,
    auditQuery.isError,
    records,
  );

  return (
    <AppShell activeHref="/admin/audit">
      <div className="space-y-6">
        <PageHeader
          title="Audit logs"
          subtitle="Inspect workspace audit records and automation traceability."
          chips={
            <>
              <StatusChip status="active" label="Workspace scoped" />
              {correlationId ? (
                <StatusChip status="processing" label="Correlation filtered" />
              ) : null}
            </>
          }
        />

        {state === "loading" ? (
          <LoadingState label="Loading audit records" />
        ) : state === "empty" ? (
          <EmptyState
            title="No audit records found"
            description="Drive, OCR, and workflow audit records will appear here after activity runs."
          />
        ) : state === "error" ? (
          <ErrorState
            title="Audit records could not load"
            description="The audit API did not return records for this workspace."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
                onClick={() => auditQuery.refetch()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Retry audit
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Audit access is not available"
            description="The current workspace role cannot view audit records."
          />
        ) : (
          <AuditRecordList records={records} />
        )}
      </div>
    </AppShell>
  );
}

function resolveState(
  override: AuditLogState | undefined,
  loading: boolean,
  error: boolean,
  records: AuditRecord[],
): AuditLogState {
  if (override)
    return override;
  if (loading)
    return "loading";
  if (error)
    return "error";
  return records.length === 0 ? "empty" : "normal";
}

function AuditRecordList({ records }: { records: AuditRecord[] }) {
  const successes = records.filter(
    (record) => normalizeOutcome(record.outcome) === "success",
  );
  const resourceTypes = new Set(records.map((record) => record.resourceType));

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <AuditMetric
          label="Records"
          value={records.length.toString()}
          detail="Current workspace"
        />
        <AuditMetric
          label="Successful"
          value={successes.length.toString()}
          detail="Recorded outcomes"
        />
        <AuditMetric
          label="Resources"
          value={resourceTypes.size.toString()}
          detail="Resource types"
        />
      </section>

      <SectionCard
        title="Records"
        description="Audit entries include actor, resource, outcome, and correlation metadata."
      >
        <div className="hidden overflow-hidden rounded-card border border-border md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-muted text-xs font-medium uppercase tracking-normal text-text-secondary">
              <tr>
                <th className="px-4 py-3">Action</th>
                <th className="px-4 py-3">Resource</th>
                <th className="px-4 py-3">Outcome</th>
                <th className="px-4 py-3">Actor</th>
                <th className="px-4 py-3">Occurred</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {records.map((record) => (
                <tr key={record.auditId} className="bg-surface">
                  <td className="px-4 py-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <AuditBadge />
                      <span className="min-w-0">
                        <span className="block truncate font-medium text-text-primary">
                          {record.action}
                        </span>
                        <span className="block truncate text-xs text-text-secondary">
                          {record.correlationId}
                        </span>
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    <span className="block">{record.resourceType}</span>
                    <span className="block text-xs">{record.resourceId}</span>
                  </td>
                  <td className="px-4 py-3">
                    <StatusChip
                      status={outcomeChip(record.outcome)}
                      label={formatOutcome(record.outcome)}
                    />
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    {record.actorId}
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    {formatDate(record.occurredAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="space-y-3 md:hidden">
          {records.map((record) => (
            <article
              key={record.auditId}
              className="rounded-card border border-border bg-surface p-4 shadow-card"
            >
              <div className="flex items-start gap-3">
                <AuditBadge />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-3">
                    <p className="truncate text-sm font-semibold text-text-primary">
                      {record.action}
                    </p>
                    <StatusChip
                      status={outcomeChip(record.outcome)}
                      label={formatOutcome(record.outcome)}
                    />
                  </div>
                  <p className="mt-2 text-sm leading-5 text-text-secondary">
                    {record.resourceType} - {record.resourceId}
                  </p>
                  <p className="mt-3 text-xs text-text-muted">
                    {formatDate(record.occurredAt)} - {record.correlationId}
                  </p>
                </div>
              </div>
            </article>
          ))}
        </div>
      </SectionCard>
    </div>
  );
}

function AuditMetric({
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

function AuditBadge() {
  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-card bg-success-soft text-success">
      <ShieldCheck className="h-4 w-4" aria-hidden="true" />
    </span>
  );
}

function outcomeChip(outcome: string): StatusKind {
  return normalizeOutcome(outcome) === "success" ? "completed" : "failed";
}

function formatOutcome(outcome: string) {
  const normalized = normalizeOutcome(outcome);
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function normalizeOutcome(outcome: string) {
  return outcome.trim().toLowerCase();
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
