import { Bot, FileText, Image as ImageIcon } from "lucide-react";

import { OcrLifecycleTrace } from "@/features/media/ocr-lifecycle-trace";
import {
  OcrApiError,
  type OcrJobDetail,
  type OcrJobStatus,
  type OcrJobSummary,
} from "@/lib/media-api";
import { StatusChip } from "@/components/ui";

type MediaJobDetailsProps = {
  job: OcrJobSummary | OcrJobDetail;
  detailLoading: boolean;
  detailError: unknown;
};

export function MediaJobDetails({
  job,
  detailLoading,
  detailError,
}: MediaJobDetailsProps) {
  const detail = isOcrJobDetail(job) ? job : null;

  return (
    <div className="space-y-5">
      <div className="rounded-card border border-border bg-surface-muted p-4">
        <div className="flex items-start gap-3">
          <MediaFileBadge contentType={job.contentType} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-text-primary">
              {job.fileName}
            </p>
            <p className="mt-1 text-xs text-text-secondary">
              {fileKind(job.contentType)} · Job {job.jobId}
            </p>
          </div>
          <StatusChip status={job.status} label={formatStatus(job.status)} />
        </div>
      </div>

      <dl className="space-y-3 text-sm">
        <DetailRow
          label="Provider"
          value={job.provider ?? "Provider pending"}
        />
        <DetailRow
          label="Attempts"
          value={`${job.attemptCount}/${job.maxAttempts}`}
        />
        <DetailRow label="Queued" value={formatDate(job.queuedAt)} />
        <DetailRow label="Updated" value={formatDate(job.updatedAt)} />
        <DetailRow label="Correlation" value={job.correlationId} />
      </dl>

      {job.failureCode ? (
        <div className="rounded-card border border-danger-soft bg-danger-soft p-4">
          <p className="text-sm font-semibold text-danger">{job.failureCode}</p>
          <p className="mt-2 text-sm leading-5 text-danger">
            {job.failureMessage ?? "OCR failed without a detailed message."}
          </p>
        </div>
      ) : null}

      {detailLoading ? <DetailLoadingState /> : null}
      {detailError ? <ProtectedDetailState error={detailError} /> : null}

      {detail ? <AuthorizedDetail detail={detail} /> : null}
    </div>
  );
}

function AuthorizedDetail({ detail }: { detail: OcrJobDetail }) {
  return (
    <>
      <OcrLifecycleTrace
        lifecycle={detail.lifecycle}
        correlationId={detail.correlationId || null}
        loading={false}
        error={false}
      />

      <OcrResultSection detail={detail} />
      <ExtractionSection detail={detail} />
    </>
  );
}

function DetailLoadingState() {
  return (
    <p
      className="rounded-card border border-info-soft bg-info-soft p-4 text-sm text-info"
      role="status"
    >
      Loading protected OCR job details...
    </p>
  );
}

function ProtectedDetailState({ error }: { error: unknown }) {
  const permissionDenied =
    error instanceof OcrApiError &&
    (error.status === 401 || error.status === 403);

  return (
    <section
      aria-label="Protected OCR job detail"
      className="rounded-card border border-danger-soft bg-danger-soft p-4"
    >
      <h3 className="text-sm font-semibold text-danger">
        {permissionDenied
          ? "You do not have access to the selected OCR job."
          : "Selected OCR job details could not load."}
      </h3>
      <p className="mt-2 text-sm leading-5 text-danger">
        {permissionDenied
          ? "Raw OCR text and structured extraction fields are protected by file access."
          : "Try selecting the job again or reload the page."}
      </p>
    </section>
  );
}

function OcrResultSection({ detail }: { detail: OcrJobDetail }) {
  const result = detail.ocrResult;

  return (
    <section className="rounded-card border border-border bg-surface p-4">
      <div className="flex items-center gap-2">
        <Bot className="h-4 w-4 text-info" aria-hidden="true" />
        <h3 className="text-sm font-semibold text-text-primary">OCR result</h3>
      </div>

      {result ? (
        <>
          <dl className="mt-4 space-y-3 text-sm">
            <DetailRow
              label="Provider"
              value={`${result.provider} ${result.providerVersion}`}
            />
            <DetailRow label="Pages" value={result.pageCount.toString()} />
            <DetailRow label="Words" value={result.wordCount.toString()} />
          </dl>
          <div className="mt-4 border-t border-border pt-4">
            <h4 className="text-sm font-semibold text-text-primary">
              Extracted text
            </h4>
            {detail.extractedText ? (
              <pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-card bg-surface-muted p-3 text-xs leading-5 text-text-primary">
                {detail.extractedText}
              </pre>
            ) : (
              <p className="mt-3 text-sm leading-5 text-text-secondary">
                OCR completed, but raw extracted text was not returned.
              </p>
            )}
          </div>
        </>
      ) : detail.status === "failed" ? (
        <p className="mt-3 text-sm leading-5 text-text-secondary" role="status">
          OCR failed before a result was produced.
        </p>
      ) : (
        <p className="mt-3 text-sm leading-5 text-text-secondary" role="status">
          OCR result is pending. Provider details and extracted text will appear
          when processing finishes.
        </p>
      )}
    </section>
  );
}

function ExtractionSection({ detail }: { detail: OcrJobDetail }) {
  const extraction = detail.extraction;

  return (
    <section
      aria-live="polite"
      aria-label="Structured extraction"
      className="rounded-card border border-border bg-surface p-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-text-primary">
          Structured extraction
        </h3>
        {extraction ? (
          <StatusChip
            status={
              extraction.status === "review_required"
                ? "review-required"
                : "completed"
            }
            label={
              extraction.status === "review_required"
                ? "Review required"
                : "Completed"
            }
          />
        ) : null}
      </div>

      {extraction ? (
        <>
          <dl className="mt-4 space-y-3 text-sm">
            <DetailRow
              label="Extractor"
              value={`${extraction.extractor} ${extraction.extractorVersion}`}
            />
            <DetailRow
              label="Aggregate confidence"
              value={formatConfidence(extraction.confidence)}
            />
          </dl>
          <ExtractionFields fields={extraction.fields} />
          <ExtractionWarnings warnings={extraction.warnings} />
        </>
      ) : detail.status === "failed" ? (
        <div className="mt-3" role="status">
          <h4 className="text-sm font-semibold text-text-primary">
            Extraction unavailable
          </h4>
          <p className="mt-1 text-sm leading-5 text-text-secondary">
            Structured extraction was not produced because OCR failed.
          </p>
        </div>
      ) : detail.status === "completed" ? (
        <div className="mt-3" role="status">
          <h4 className="text-sm font-semibold text-text-primary">
            Extraction is pending
          </h4>
          <p className="mt-1 text-sm leading-5 text-text-secondary">
            OCR completed recently and structured fields will appear when the
            extraction workflow finishes.
          </p>
        </div>
      ) : (
        <p className="mt-3 text-sm leading-5 text-text-secondary" role="status">
          Structured extraction will appear after OCR processing completes.
        </p>
      )}
    </section>
  );
}

function ExtractionFields({
  fields,
}: {
  fields: NonNullable<OcrJobDetail["extraction"]>["fields"];
}) {
  if (fields.length === 0) {
    return (
      <p className="mt-4 text-sm leading-5 text-text-secondary">
        No structured fields were returned.
      </p>
    );
  }

  return (
    <dl className="mt-4 space-y-4 border-t border-border pt-4">
      {fields.map((field) => (
        <div key={field.fieldKey} className="space-y-2">
          <dt className="text-sm font-semibold text-text-primary">
            {field.label}
          </dt>
          <dd className="break-words text-sm leading-5 text-text-primary">
            {field.displayValue}
          </dd>
          <dd className="text-xs text-text-secondary">
            {formatConfidence(field.confidence)}
          </dd>
          {field.provenance.length > 0 ? (
            <dd>
              <ul
                aria-label={`Provenance for ${field.label}`}
                className="space-y-1 text-xs text-text-secondary"
              >
                {field.provenance.map((source) => (
                  <li key={`${source.ocrWordId}-${source.sourceRole}`}>
                    {formatProvenance(source)}
                  </li>
                ))}
              </ul>
            </dd>
          ) : (
            <dd className="text-xs text-text-secondary">
              Provenance not available.
            </dd>
          )}
        </div>
      ))}
    </dl>
  );
}

function ExtractionWarnings({
  warnings,
}: {
  warnings: NonNullable<OcrJobDetail["extraction"]>["warnings"];
}) {
  if (warnings.length === 0) {
    return (
      <p className="mt-4 text-sm text-text-secondary">
        No extraction warnings.
      </p>
    );
  }

  return (
    <div className="mt-4 border-t border-border pt-4">
      <h4 className="text-sm font-semibold text-text-primary">Warnings</h4>
      <ul
        aria-label="Extraction warnings"
        className="mt-2 list-disc space-y-2 pl-5 text-sm leading-5 text-warning"
      >
        {warnings.map((warning) => (
          <li key={`${warning.code}-${warning.fieldKey ?? "general"}`}>
            {warning.message}
          </li>
        ))}
      </ul>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="shrink-0 text-text-secondary">{label}</dt>
      <dd className="break-words text-right font-medium text-text-primary">
        {value}
      </dd>
    </div>
  );
}

function MediaFileBadge({ contentType }: { contentType: string }) {
  const kind = fileKind(contentType);
  const Icon = kind === "Image" ? ImageIcon : FileText;

  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-card bg-info-soft text-info">
      {kind === "PDF" ? (
        <span className="text-[10px] font-semibold">PDF</span>
      ) : (
        <Icon className="h-4 w-4" aria-hidden="true" />
      )}
    </span>
  );
}

function isOcrJobDetail(
  job: OcrJobSummary | OcrJobDetail,
): job is OcrJobDetail {
  return "extractedText" in job;
}

function fileKind(contentType: string) {
  if (contentType === "application/pdf") {
    return "PDF";
  }
  if (contentType.startsWith("image/")) {
    return "Image";
  }
  return "File";
}

function formatStatus(status: OcrJobStatus) {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

function formatConfidence(confidence: number | null) {
  return confidence === null ? "Not available" : `${confidence}% confidence`;
}

function formatProvenance(
  source: NonNullable<
    NonNullable<
      OcrJobDetail["extraction"]
    >["fields"][number]["provenance"][number]
  >,
) {
  const sourceKind =
    source.sourceKind === "tesseract_tsv" ? "Tesseract TSV" : "PDF text layer";
  return `Page ${source.pageNumber}, line ${source.lineNumber}, ${sourceKind}`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
