"use client";

import { useQuery } from "@tanstack/react-query";
import { ExternalLink, RefreshCw, Search as SearchIcon } from "lucide-react";
import Link from "next/link";
import { FormEvent, useState } from "react";

import { AppShell } from "@/components/layout/app-shell";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PermissionDeniedState,
  SearchInput,
  SectionCard,
  StatusChip,
  type StatusKind,
} from "@/components/ui";
import type { SearchResult, SearchResponse } from "@/lib/search-api";
import { fetchSearchResults } from "@/lib/search-api";

export type SearchScreenState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

type SearchScreenProps = {
  initialQuery?: string;
  stateOverride?: SearchScreenState;
};

const mockSearchResponse: SearchResponse = {
  query: "TEST-INV-2026-0001",
  backend: "meilisearch",
  results: [
    {
      id: "srch_demo_invoice_mock",
      sourceType: "demo_invoice_extraction",
      sourceId: "dinv_demo_invoice_mock",
      title: "Fake/test invoice TEST-INV-2026-0001",
      summary:
        "Demo Supplies S.L. fake/test invoice, total EUR 124.00, due 2026-06-15.",
      resourceHref: "/app/demo/invoice-automation",
      correlationId: "corr_demo_invoice_mock",
      status: "indexed",
      metadata: {
        invoiceNumber: "TEST-INV-2026-0001",
        isTestData: true,
      },
      createdAt: "2026-05-25T09:00:13Z",
    },
  ],
};

export function SearchScreen({
  initialQuery = "",
  stateOverride,
}: SearchScreenProps) {
  const [query, setQuery] = useState(initialQuery);
  const [submittedQuery, setSubmittedQuery] = useState(initialQuery.trim());
  const searchQuery = useQuery({
    queryKey: ["search", submittedQuery],
    queryFn: () => fetchSearchResults(submittedQuery),
    enabled: stateOverride === undefined && submittedQuery.length > 0,
  });
  const response =
    stateOverride === "normal" ? mockSearchResponse : searchQuery.data;
  const results = response?.results ?? [];
  const state = resolveState(
    stateOverride,
    searchQuery.isPending && submittedQuery.length > 0,
    searchQuery.isError,
    submittedQuery,
    results,
  );

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmittedQuery(query.trim());
  }

  return (
    <AppShell activeHref="/app/search">
      <div className="space-y-6">
        <PageHeader
          title="Search"
          subtitle="Find indexed workspace documents and automation outputs."
          chips={
            <>
              <StatusChip status="active" label="Workspace search" />
              {response?.backend ? (
                <StatusChip status="processing" label={response.backend} />
              ) : null}
            </>
          }
        />

        <SectionCard title="Query">
          <form
            className="flex flex-col gap-3 sm:flex-row sm:items-center"
            onSubmit={handleSubmit}
          >
            <SearchInput
              aria-label="Search indexed documents"
              className="min-w-0 flex-1"
              placeholder="Search invoices, files, OCR text..."
              value={query}
              onChange={(event) => setQuery(event.currentTarget.value)}
            />
            <button
              type="submit"
              className="inline-flex min-h-10 items-center justify-center gap-2 rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover"
            >
              <SearchIcon className="h-4 w-4" aria-hidden="true" />
              Search
            </button>
          </form>
        </SectionCard>

        {state === "loading" ? (
          <LoadingState label="Loading search results" />
        ) : state === "empty" ? (
          <EmptyState
            title={
              submittedQuery.length === 0
                ? "Enter a search query"
                : "No search results found"
            }
            description={
              submittedQuery.length === 0
                ? "Indexed demo invoice results appear here after automation runs."
                : "Try the seeded invoice number or wait for indexing to complete."
            }
          />
        ) : state === "error" ? (
          <ErrorState
            title="Search results could not load"
            description="The search API did not return indexed documents for this query."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
                onClick={() => searchQuery.refetch()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Retry search
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Search access is not available"
            description="The current workspace role cannot query indexed documents."
          />
        ) : (
          <SearchResults
            query={response?.query ?? submittedQuery}
            backend={response?.backend ?? "search"}
            results={results}
          />
        )}
      </div>
    </AppShell>
  );
}

function resolveState(
  override: SearchScreenState | undefined,
  loading: boolean,
  error: boolean,
  submittedQuery: string,
  results: SearchResult[],
): SearchScreenState {
  if (override) {
    return override;
  }
  if (submittedQuery.length === 0) {
    return "empty";
  }
  if (loading) {
    return "loading";
  }
  if (error) {
    return "error";
  }
  return results.length === 0 ? "empty" : "normal";
}

function SearchResults({
  query,
  backend,
  results,
}: {
  query: string;
  backend: string;
  results: SearchResult[];
}) {
  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <SearchMetric
          label="Results"
          value={results.length.toString()}
          detail={`Query: ${query}`}
        />
        <SearchMetric label="Backend" value={backend} detail="Search source" />
        <SearchMetric
          label="Indexed"
          value={results
            .filter((result) => result.status === "indexed")
            .length.toString()}
          detail="Ready documents"
        />
      </section>

      <SectionCard
        title="Results"
        description="Search documents include sanitized metadata and workspace-scoped links."
      >
        <div className="space-y-3">
          {results.map((result) => (
            <article
              key={result.id}
              className="rounded-card border border-border bg-surface p-4 shadow-card"
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-card bg-info-soft text-info">
                      <SearchIcon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <h2 className="truncate text-sm font-semibold text-text-primary">
                      {result.title}
                    </h2>
                  </div>
                  <p className="mt-3 text-sm leading-6 text-text-secondary">
                    {result.summary}
                  </p>
                  <p className="mt-3 text-xs text-text-muted">
                    {result.sourceType} - {result.correlationId}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <StatusChip
                    status={searchStatusChip(result.status)}
                    label={result.status}
                  />
                  <Link
                    href={result.resourceHref}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-card border border-border text-text-secondary hover:bg-surface-muted hover:text-text-primary"
                    aria-label={`Open ${result.title}`}
                  >
                    <ExternalLink className="h-4 w-4" aria-hidden="true" />
                  </Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      </SectionCard>
    </div>
  );
}

function SearchMetric({
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
      <p className="mt-2 truncate text-2xl font-semibold text-text-primary">
        {value}
      </p>
      <p className="mt-1 truncate text-sm text-text-secondary">{detail}</p>
    </div>
  );
}

function searchStatusChip(status: string): StatusKind {
  if (status === "indexed") {
    return "completed";
  }
  if (status === "failed") {
    return "failed";
  }
  if (status === "pending") {
    return "queued";
  }
  return "processing";
}
