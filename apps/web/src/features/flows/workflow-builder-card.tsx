import { Activity, Bell, FileText, Zap } from "lucide-react";
import type { ReactNode } from "react";

import { LoadingState, SectionCard, StatusChip } from "@/components/ui";
import { cn } from "@/lib/cn";
import type {
  WorkflowActionType,
  WorkflowDefinition,
  WorkflowDetail,
  WorkflowSummary,
} from "@/lib/flows-api";
import {
  formatActionType,
  nodeTone,
  triggerTitle,
  workflowStatusChip,
} from "./flows-view-helpers";

type WorkflowBuilderCardProps = {
  workflowSummary: WorkflowSummary | null;
  workflow: WorkflowDetail | undefined | null;
};

export function WorkflowBuilderCard({
  workflowSummary,
  workflow,
}: WorkflowBuilderCardProps) {
  const definition = workflow?.definition;

  return (
    <SectionCard
      title="Vertical builder"
      description={
        workflowSummary
          ? `${workflowSummary.triggerEventType ?? workflowSummary.triggerType} trigger - ${workflowSummary.stepCount} steps`
          : undefined
      }
      action={
        workflowSummary ? (
          <StatusChip
            status={workflowStatusChip(workflowSummary.status)}
            label={`v${workflowSummary.currentVersionNumber}`}
          />
        ) : null
      }
    >
      {!workflowSummary ? (
        <p className="text-sm text-text-secondary">No workflow selected.</p>
      ) : !definition ? (
        <LoadingState label="Loading workflow definition" />
      ) : (
        <VerticalWorkflow definition={definition} />
      )}
    </SectionCard>
  );
}

function VerticalWorkflow({ definition }: { definition: WorkflowDefinition }) {
  return (
    <div className="space-y-3">
      <FlowNode
        title={triggerTitle(definition.trigger)}
        subtitle="Trigger"
        tone="info"
        icon={<Zap className="h-4 w-4" aria-hidden="true" />}
      />
      <div className="ml-5 h-5 border-l border-border" />
      {definition.steps.map((step, index) => (
        <div key={step.id}>
          <FlowNode
            title={step.name}
            subtitle={formatActionType(step.action.type)}
            tone={nodeTone(step.action.type)}
            icon={actionIcon(step.action.type)}
          />
          {index < definition.steps.length - 1 ? (
            <div className="ml-5 h-5 border-l border-border" />
          ) : null}
        </div>
      ))}
    </div>
  );
}

function FlowNode({
  title,
  subtitle,
  tone,
  icon,
}: {
  title: string;
  subtitle: string;
  tone: "info" | "success" | "warning";
  icon: ReactNode;
}) {
  const toneClass =
    tone === "success"
      ? "bg-success-soft text-success"
      : tone === "warning"
        ? "bg-warning-soft text-warning"
        : "bg-info-soft text-info";

  return (
    <div className="flex items-start gap-3 rounded-card border border-border bg-surface p-4">
      <span
        className={cn(
          "flex h-10 w-10 shrink-0 items-center justify-center rounded-card",
          toneClass,
        )}
      >
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <p
          className="truncate text-sm font-semibold text-text-primary"
          title={title}
        >
          {title}
        </p>
        <p
          className="mt-1 truncate text-xs text-text-secondary"
          title={subtitle}
        >
          {subtitle}
        </p>
      </div>
    </div>
  );
}

function actionIcon(actionType: WorkflowActionType) {
  if (actionType === "create_notification") {
    return <Bell className="h-4 w-4" aria-hidden="true" />;
  }
  if (actionType === "create_audit_entry") {
    return <Activity className="h-4 w-4" aria-hidden="true" />;
  }
  return <FileText className="h-4 w-4" aria-hidden="true" />;
}
