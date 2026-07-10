import Link from "next/link";

import { SectionCard, StatusChip } from "@/components/ui";
import type { StatusKind } from "@/components/ui/status-chip";
import type { OcrJobLifecycle, OcrLifecycleEntry } from "@/lib/media-api";

type OcrLifecycleTraceProps = {
  lifecycle: OcrJobLifecycle | null;
  correlationId: string | null;
  loading: boolean;
  error: boolean;
};

export function OcrLifecycleTrace({
  lifecycle,
  correlationId,
  loading,
  error,
}: OcrLifecycleTraceProps) {
  return (
    <SectionCard
      title="Lifecycle"
      description="Durable facts for this OCR job and its correlated workflow."
      action={
        lifecycle ? <LifecycleStateChip lifecycle={lifecycle} /> : undefined
      }
      contentClassName="space-y-4"
    >
      {loading ? (
        <p role="status" className="text-sm text-text-secondary">
          Loading lifecycle...
        </p>
      ) : error ? (
        <div
          role="alert"
          className="rounded-card border border-danger-soft bg-danger-soft p-3 text-sm text-danger"
        >
          Lifecycle diagnostics could not be loaded. The job summary remains
          available.
        </div>
      ) : lifecycle ? (
        <LifecycleContent lifecycle={lifecycle} />
      ) : (
        <p className="text-sm leading-5 text-text-secondary">
          No durable lifecycle evidence is available for this job yet.
        </p>
      )}

      {correlationId ? (
        <Link
          href={{
            pathname: "/admin/audit",
            query: { correlationId },
          }}
          className="inline-flex min-h-10 items-center rounded-card border border-border-strong bg-surface px-3 text-sm font-medium text-text-primary hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          View correlated audit log
        </Link>
      ) : null}
    </SectionCard>
  );
}

function LifecycleContent({ lifecycle }: { lifecycle: OcrJobLifecycle }) {
  return (
    <>
      {lifecycle.state === "active" ? (
        <div
          role="status"
          className="flex items-start gap-3 rounded-card border border-warning-soft bg-warning-soft p-3"
        >
          <StatusChip status="queued" label="Awaiting" />
          <p className="text-sm leading-5 text-text-primary">
            Durable state shows that this lifecycle is still progressing.
          </p>
        </div>
      ) : lifecycle.state === "partial" ? (
        <div className="rounded-card border border-border bg-surface-muted p-3">
          <p className="text-sm font-medium text-text-primary">
            Some durable lifecycle evidence is unavailable
          </p>
          <p className="mt-1 text-xs leading-5 text-text-secondary">
            Missing evidence is shown as unknown and is not treated as broker
            delivery, consumption, retry, or completion.
          </p>
        </div>
      ) : null}

      {lifecycle.entries.length > 0 ? (
        <ol aria-label="OCR lifecycle trace" className="space-y-3">
          {lifecycle.entries.map((entry) => (
            <LifecycleCard key={entry.entryId} entry={entry} />
          ))}
        </ol>
      ) : (
        <p className="text-sm leading-5 text-text-secondary">
          No lifecycle entries have been recorded.
        </p>
      )}
    </>
  );
}

function LifecycleCard({ entry }: { entry: OcrLifecycleEntry }) {
  return (
    <li className="rounded-card border border-border bg-surface-muted p-3 md:border-x-0 md:border-t-0 md:bg-transparent md:px-0 md:pb-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-normal text-text-muted">
            {phaseLabel(entry.phase)} ·{" "}
            {entry.observed ? "Observed" : "Not observed"}
          </p>
          <p className="mt-1 text-sm font-semibold text-text-primary">
            {entry.label}
          </p>
        </div>
        <StatusChip
          status={statusKind(entry.status)}
          label={statusLabel(entry.status)}
          className="shrink-0"
        />
      </div>

      <div className="mt-2 space-y-1 text-xs leading-5 text-text-secondary">
        <p>
          {entry.occurredAt
            ? formatLifecycleDate(entry.occurredAt)
            : "Timestamp not available"}
          {entry.source ? ` · Source ${entry.source}` : ""}
        </p>
        {entry.event ? <EventDetails entry={entry} /> : null}
        {entry.workflow ? <WorkflowDetails entry={entry} /> : null}
        {entry.retry ? <RetryDetails entry={entry} /> : null}
        {entry.failure?.reason ? (
          <p className="text-danger">
            {entry.failure.code ? `${entry.failure.code} · ` : ""}
            {entry.failure.reason}
          </p>
        ) : null}
        {entry.resource?.resourceId ? (
          <p className="break-all">
            {entry.resource.resourceType} · {entry.resource.resourceId}
          </p>
        ) : null}
      </div>
    </li>
  );
}

function EventDetails({ entry }: { entry: OcrLifecycleEntry }) {
  const event = entry.event;
  if (!event) return null;

  return (
    <>
      <p className="break-all">
        {event.eventType} v{event.eventVersion} · Event {event.eventId}
      </p>
      {event.causationId ? (
        <p className="break-all">Caused by {event.causationId}</p>
      ) : null}
      <p>
        {event.publicationState === "publish_recorded"
          ? `Publisher send recorded${
              event.publishedAt
                ? ` ${formatLifecycleDate(event.publishedAt)}`
                : ""
            }`
          : "Outbox awaiting publication"}
      </p>
      {event.consumptions.map((consumption) => (
        <p key={`${consumption.consumer}-${consumption.consumedAt}`}>
          Application consumption recorded · {consumption.consumer} ·{" "}
          {formatLifecycleDate(consumption.consumedAt)}
        </p>
      ))}
    </>
  );
}

function WorkflowDetails({ entry }: { entry: OcrLifecycleEntry }) {
  const workflow = entry.workflow;
  if (!workflow) return null;

  return (
    <>
      <p className="break-all">Execution {workflow.executionId}</p>
      <p className="break-all">
        Workflow {workflow.workflowId}
        {workflow.workflowVersionId
          ? ` · Version ${workflow.workflowVersionNumber} (${workflow.workflowVersionId})`
          : ""}
      </p>
      {workflow.stepKey ? (
        <p className="break-all">
          Step {workflow.stepKey}
          {workflow.actionType ? ` · ${workflow.actionType}` : ""}
        </p>
      ) : null}
      {workflow.retryCount > 0 ? (
        <p>
          {workflow.retryCount} workflow{" "}
          {workflow.retryCount === 1 ? "retry" : "retries"} recorded
        </p>
      ) : null}
    </>
  );
}

function RetryDetails({ entry }: { entry: OcrLifecycleEntry }) {
  const retry = entry.retry;
  if (!retry) return null;

  return (
    <>
      <p>
        Attempt {retry.attemptCount} of {retry.maxAttempts}
      </p>
      {retry.nextAttemptAt ? (
        <p>Next attempt {formatLifecycleDate(retry.nextAttemptAt)}</p>
      ) : null}
    </>
  );
}

function LifecycleStateChip({ lifecycle }: { lifecycle: OcrJobLifecycle }) {
  if (lifecycle.outcome === "failed") {
    return <StatusChip status="failed" label="Failed" />;
  }
  if (lifecycle.state === "active") {
    return <StatusChip status="processing" label="In progress" />;
  }
  if (lifecycle.state === "partial") {
    return <StatusChip status="disabled" label="Partial" />;
  }
  return <StatusChip status="completed" label="Complete" />;
}

function statusKind(status: string): StatusKind {
  if (status === "failed" || status === "failure") return "failed";
  if (status === "retrying") return "retrying";
  if (
    status === "processing" ||
    status === "running" ||
    status === "indexing"
  ) {
    return "processing";
  }
  if (status === "queued" || status === "awaiting") return "queued";
  if (status === "unknown") return "disabled";
  return "completed";
}

function statusLabel(status: string) {
  if (status === "awaiting") return "Pending";
  return status
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function phaseLabel(phase: OcrLifecycleEntry["phase"]) {
  return phase.charAt(0).toUpperCase() + phase.slice(1);
}

function formatLifecycleDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
