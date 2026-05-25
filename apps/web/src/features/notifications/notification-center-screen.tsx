"use client";

import { useQuery } from "@tanstack/react-query";
import { Bell, MailOpen, RefreshCw } from "lucide-react";

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
import type { NotificationRecord } from "@/lib/notifications-api";
import { fetchNotifications } from "@/lib/notifications-api";

export type NotificationCenterState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

type NotificationCenterScreenProps = {
  correlationId?: string;
  stateOverride?: NotificationCenterState;
};

const mockNotifications: NotificationRecord[] = [
  {
    notificationId: "ntf_demo_invoice_mock",
    title: "Fake/test invoice ready for review",
    body: "The seeded invoice automation extracted fake/test fields and requested search indexing.",
    severity: "info",
    status: "unread",
    sourceType: "workflow_execution",
    sourceId: "wfe_demo_invoice_mock",
    correlationId: "corr_demo_invoice_mock",
    createdAt: "2026-05-25T09:00:10Z",
    readAt: null,
  },
];

export function NotificationCenterScreen({
  correlationId,
  stateOverride,
}: NotificationCenterScreenProps) {
  const notificationsQuery = useQuery({
    queryKey: ["notifications", correlationId ?? ""],
    queryFn: () => fetchNotifications(correlationId),
    enabled: stateOverride === undefined,
  });
  const notifications =
    stateOverride === "normal"
      ? mockNotifications
      : (notificationsQuery.data?.notifications ?? []);
  const state = resolveState(
    stateOverride,
    notificationsQuery.isPending,
    notificationsQuery.isError,
    notifications,
  );

  return (
    <AppShell activeHref="/app/notifications">
      <div className="space-y-6">
        <PageHeader
          title="Notifications"
          subtitle="Review workspace notifications created by flows and system activity."
          chips={
            <>
              <StatusChip status="active" label="Inbox" />
              {correlationId ? (
                <StatusChip status="processing" label="Correlation filtered" />
              ) : null}
            </>
          }
        />

        {state === "loading" ? (
          <LoadingState label="Loading notifications" />
        ) : state === "empty" ? (
          <EmptyState
            title="No notifications found"
            description="Workflow-created notifications will appear here after automation runs."
          />
        ) : state === "error" ? (
          <ErrorState
            title="Notifications could not load"
            description="The notification API did not return inbox records for this workspace."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
                onClick={() => notificationsQuery.refetch()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Retry notifications
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Notification access is not available"
            description="The current workspace role cannot view notification records."
          />
        ) : (
          <NotificationList notifications={notifications} />
        )}
      </div>
    </AppShell>
  );
}

function resolveState(
  override: NotificationCenterState | undefined,
  loading: boolean,
  error: boolean,
  notifications: NotificationRecord[],
): NotificationCenterState {
  if (override) {
    return override;
  }
  if (loading) {
    return "loading";
  }
  if (error) {
    return "error";
  }
  return notifications.length === 0 ? "empty" : "normal";
}

function NotificationList({
  notifications,
}: {
  notifications: NotificationRecord[];
}) {
  const unread = notifications.filter(
    (notification) => notification.status === "unread",
  ).length;

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <NotificationMetric
          label="Notifications"
          value={notifications.length.toString()}
          detail="Current workspace"
        />
        <NotificationMetric
          label="Unread"
          value={unread.toString()}
          detail="Needs attention"
        />
        <NotificationMetric
          label="Sources"
          value={new Set(
            notifications.map((item) => item.sourceType),
          ).size.toString()}
          detail="Workflow and system"
        />
      </section>

      <SectionCard
        title="Inbox"
        description="Notification records are scoped by workspace and optional correlation ID."
      >
        <div className="hidden overflow-hidden rounded-card border border-border md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-muted text-xs font-medium uppercase tracking-normal text-text-secondary">
              <tr>
                <th className="px-4 py-3">Notification</th>
                <th className="px-4 py-3">Severity</th>
                <th className="px-4 py-3">Source</th>
                <th className="px-4 py-3">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {notifications.map((notification) => (
                <tr key={notification.notificationId} className="bg-surface">
                  <td className="px-4 py-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <NotificationBadge status={notification.status} />
                      <span className="min-w-0">
                        <span className="block truncate font-medium text-text-primary">
                          {notification.title}
                        </span>
                        <span className="block truncate text-xs text-text-secondary">
                          {notification.body}
                        </span>
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <StatusChip
                      status={severityChip(notification.severity)}
                      label={notification.severity}
                    />
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    <span className="block">{notification.sourceType}</span>
                    <span className="block text-xs">
                      {notification.sourceId}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    {formatDate(notification.createdAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="space-y-3 md:hidden">
          {notifications.map((notification) => (
            <article
              key={notification.notificationId}
              className="rounded-card border border-border bg-surface p-4 shadow-card"
            >
              <div className="flex items-start gap-3">
                <NotificationBadge status={notification.status} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-3">
                    <p className="truncate text-sm font-semibold text-text-primary">
                      {notification.title}
                    </p>
                    <StatusChip
                      status={severityChip(notification.severity)}
                      label={notification.severity}
                    />
                  </div>
                  <p className="mt-2 text-sm leading-5 text-text-secondary">
                    {notification.body}
                  </p>
                  <p className="mt-3 text-xs text-text-muted">
                    {formatDate(notification.createdAt)} -{" "}
                    {notification.correlationId}
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

function NotificationMetric({
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

function NotificationBadge({
  status,
}: {
  status: NotificationRecord["status"];
}) {
  const Icon = status === "read" ? MailOpen : Bell;

  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-card bg-info-soft text-info">
      <Icon className="h-4 w-4" aria-hidden="true" />
    </span>
  );
}

function severityChip(severity: NotificationRecord["severity"]): StatusKind {
  if (severity === "danger") {
    return "failed";
  }
  if (severity === "warning") {
    return "queued";
  }
  return "processing";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
