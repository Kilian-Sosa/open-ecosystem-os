import {
  Activity,
  Bell,
  Bot,
  CheckCircle2,
  Database,
  File,
  FileText,
  Folder,
  Gauge,
  HardDrive,
  Search,
  Settings,
  UploadCloud,
  Workflow,
  Zap,
} from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { AppShell } from "@/components/layout/app-shell";
import {
  ActivityItem,
  EmptyState,
  ErrorState,
  LoadingState,
  MetricCard,
  PageHeader,
  QuickActionCard,
  RightInspectorPanel,
  SectionCard,
  StatusChip,
} from "@/components/ui";
import type {
  DashboardMockData,
  DashboardState,
  IconToken,
} from "@/lib/dashboard-mock-data";

type DashboardScreenProps = {
  data: DashboardMockData;
  state?: DashboardState;
};

const iconMap: Record<IconToken, ReactNode> = {
  activity: <Activity className="h-4 w-4" aria-hidden="true" />,
  automation: <Zap className="h-5 w-5" aria-hidden="true" />,
  database: <Database className="h-4 w-4" aria-hidden="true" />,
  document: <FileText className="h-5 w-5" aria-hidden="true" />,
  file: <Folder className="h-5 w-5" aria-hidden="true" />,
  health: <Gauge className="h-4 w-4" aria-hidden="true" />,
  notification: <Bell className="h-4 w-4" aria-hidden="true" />,
  ocr: <Bot className="h-5 w-5" aria-hidden="true" />,
  search: <Search className="h-5 w-5" aria-hidden="true" />,
  storage: <HardDrive className="h-5 w-5" aria-hidden="true" />,
  upload: <UploadCloud className="h-5 w-5" aria-hidden="true" />,
  workflow: <Workflow className="h-4 w-4" aria-hidden="true" />,
};

export function DashboardScreen({
  data,
  state = "normal",
}: DashboardScreenProps) {
  const inspector =
    state === "normal" ? <DashboardInspector data={data} /> : undefined;

  return (
    <AppShell activeHref="/app/dashboard" inspector={inspector}>
      <div className="space-y-6">
        <PageHeader
          title="Workspace Dashboard"
          subtitle="Monitor files, OCR work, automations, notifications, and system health from one place."
          chips={
            <>
              <StatusChip status="active" label="Workspace active" />
              <StatusChip status="healthy" label="Systems healthy" />
            </>
          }
          primaryAction={
            <Link
              href="/app/drive"
              className="inline-flex min-h-10 items-center gap-2 rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover"
            >
              <UploadCloud className="h-4 w-4" aria-hidden="true" />
              Upload invoice
            </Link>
          }
          secondaryAction={
            <button
              type="button"
              className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
            >
              <Settings className="h-4 w-4" aria-hidden="true" />
              Customize
            </button>
          }
        />

        {state === "loading" ? (
          <LoadingState />
        ) : state === "empty" ? (
          <EmptyState
            title="No workspace activity yet"
            description="Upload an invoice or create an automation to populate files, jobs, notifications, and audit activity."
            action={
              <Link
                href="/app/drive"
                className="inline-flex min-h-10 items-center rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover"
              >
                Upload first invoice
              </Link>
            }
          />
        ) : state === "error" ? (
          <ErrorState
            title="Dashboard data could not load"
            description="The workspace shell is available, but the mock dashboard feed failed to resolve."
            action={
              <Link
                href="/app/dashboard"
                className="inline-flex min-h-10 items-center rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
              >
                Retry dashboard
              </Link>
            }
          />
        ) : (
          <DashboardNormalState data={data} />
        )}
      </div>
    </AppShell>
  );
}

function DashboardNormalState({ data }: { data: DashboardMockData }) {
  return (
    <div className="space-y-6">
      <section
        aria-label="Dashboard metrics"
        className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"
      >
        {data.metrics.map((metric) => (
          <MetricCard
            key={metric.id}
            label={metric.label}
            value={metric.value}
            detail={metric.detail}
            trend={metric.trend}
            progress={metric.progress}
            tone={metric.tone}
            icon={iconMap[metric.icon]}
          />
        ))}
      </section>

      <section className="grid gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]">
        <RecentFilesCard data={data} />
        <AutomationsCard data={data} />
      </section>

      <section className="grid gap-6 xl:grid-cols-3">
        <ProcessingJobsCard data={data} />
        <SystemStatusCard data={data} className="xl:hidden" />
        <ActivityCard data={data} className="xl:col-span-2" />
      </section>

      <SectionCard
        title="Quick actions"
        description="Start the MVP workflow or jump into supporting workspace tools."
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {data.quickActions.map((action) => (
            <QuickActionCard
              key={action.id}
              title={action.title}
              description={action.description}
              icon={iconMap[action.icon]}
              tone={action.tone}
            />
          ))}
        </div>
      </SectionCard>
    </div>
  );
}

function RecentFilesCard({ data }: { data: DashboardMockData }) {
  return (
    <SectionCard
      title="Recent files"
      action={
        <Link
          href="/app/drive"
          className="text-sm font-medium text-primary hover:text-primary-hover"
        >
          View all
        </Link>
      }
    >
      <div className="divide-y divide-border">
        {data.recentFiles.map((file) => (
          <div
            key={file.id}
            className="grid gap-3 py-3 sm:grid-cols-[minmax(0,1fr)_160px_80px] sm:items-center"
          >
            <div className="flex min-w-0 items-center gap-3">
              <FileTypeIcon type={file.type} />
              <span className="truncate text-sm font-medium text-text-primary">
                {file.name}
              </span>
            </div>
            <span className="text-sm text-text-secondary">{file.location}</span>
            <time className="text-sm text-text-muted sm:text-right">
              {file.updatedAt}
            </time>
          </div>
        ))}
      </div>
    </SectionCard>
  );
}

function AutomationsCard({ data }: { data: DashboardMockData }) {
  return (
    <SectionCard
      title="Active automations"
      action={
        <Link
          href="/app/flows"
          className="text-sm font-medium text-primary hover:text-primary-hover"
        >
          View all
        </Link>
      }
    >
      <div className="space-y-3">
        {data.automations.map((automation) => (
          <div
            key={automation.id}
            className="flex items-start justify-between gap-4 rounded-card border border-border p-3"
          >
            <div className="min-w-0">
              <p className="text-sm font-semibold text-text-primary">
                {automation.name}
              </p>
              <p className="mt-1 text-xs leading-5 text-text-secondary">
                {automation.trigger}
              </p>
            </div>
            <StatusChip status={automation.status} />
          </div>
        ))}
      </div>
    </SectionCard>
  );
}

function ProcessingJobsCard({ data }: { data: DashboardMockData }) {
  return (
    <SectionCard
      title="Processing jobs"
      action={
        <Link
          href="/app/media"
          className="text-sm font-medium text-primary hover:text-primary-hover"
        >
          View all
        </Link>
      }
    >
      <div className="space-y-4">
        {data.processingJobs.map((job) => (
          <div key={job.id} className="space-y-2">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-text-primary">
                  {job.name}
                </p>
                <p className="mt-1 text-xs text-text-secondary">{job.detail}</p>
              </div>
              <StatusChip status={job.status} />
            </div>
            <div className="flex items-center gap-3">
              <div
                className="h-2 flex-1 rounded-full bg-surface-muted"
                role="progressbar"
                aria-label={`${job.name} progress`}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={job.progress}
              >
                <div
                  className="h-2 rounded-full bg-primary"
                  style={{ width: `${job.progress}%` }}
                />
              </div>
              <span className="w-10 text-right text-xs text-text-secondary">
                {job.progress}%
              </span>
            </div>
          </div>
        ))}
      </div>
    </SectionCard>
  );
}

function ActivityCard({
  data,
  className,
}: {
  data: DashboardMockData;
  className?: string;
}) {
  return (
    <SectionCard
      title="Recent activity"
      action={
        <Link
          href="/admin/audit"
          className="text-sm font-medium text-primary hover:text-primary-hover"
        >
          View all
        </Link>
      }
      className={className}
    >
      <div className="divide-y divide-border">
        {data.activity.map((activity) => (
          <ActivityItem
            key={activity.id}
            icon={iconMap[activity.icon]}
            title={activity.title}
            meta={activity.meta}
            time={activity.time}
            className="py-3"
          />
        ))}
      </div>
    </SectionCard>
  );
}

function SystemStatusCard({
  data,
  className,
}: {
  data: DashboardMockData;
  className?: string;
}) {
  return (
    <SectionCard
      title="System status"
      description="Runtime services for the invoice automation path."
      className={className}
    >
      <div className="space-y-3">
        {data.services.map((service) => (
          <div
            key={service.id}
            className="flex items-start justify-between gap-4"
          >
            <div className="min-w-0">
              <p className="text-sm font-medium text-text-primary">
                {service.name}
              </p>
              <p className="mt-1 text-xs text-text-secondary">
                {service.detail}
              </p>
            </div>
            <StatusChip status={service.status} />
          </div>
        ))}
      </div>
    </SectionCard>
  );
}

function DashboardInspector({ data }: { data: DashboardMockData }) {
  return (
    <RightInspectorPanel
      title="System and storage"
      description="Context for the current workspace slice."
    >
      <div className="space-y-5">
        <div className="rounded-card border border-border bg-surface-muted p-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-text-primary">Storage</p>
            <p className="text-sm font-semibold text-primary">
              {data.storage.percentUsed}%
            </p>
          </div>
          <div
            className="mt-3 h-2 rounded-full bg-surface"
            role="progressbar"
            aria-label="Workspace storage used"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={data.storage.percentUsed}
          >
            <div
              className="h-full rounded-full bg-primary"
              style={{ width: `${data.storage.percentUsed}%` }}
            />
          </div>
          <p className="mt-3 text-xs text-text-secondary">
            {data.storage.usedLabel} of {data.storage.totalLabel} used
          </p>
        </div>
        <SystemStatusCard data={data} />
        <div className="rounded-card border border-border bg-surface p-4">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-success" aria-hidden="true" />
            <p className="text-sm font-semibold text-text-primary">
              Audit ready
            </p>
          </div>
          <p className="mt-2 text-sm leading-5 text-text-secondary">
            Recent workflow and OCR activity includes correlation IDs for
            traceability.
          </p>
        </div>
      </div>
    </RightInspectorPanel>
  );
}

function FileTypeIcon({
  type,
}: {
  type: "pdf" | "figma" | "document" | "markdown";
}) {
  const label = type.toUpperCase();

  return (
    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-card bg-info-soft text-[10px] font-semibold text-info">
      {label === "DOCUMENT" ? (
        <FileText className="h-4 w-4" aria-hidden="true" />
      ) : label === "MARKDOWN" ? (
        <File className="h-4 w-4" aria-hidden="true" />
      ) : (
        label
      )}
    </span>
  );
}
