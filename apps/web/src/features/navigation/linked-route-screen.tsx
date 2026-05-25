import type { LucideIcon } from "lucide-react";

import { AppShell } from "@/components/layout/app-shell";
import { PageHeader, SectionCard, StatusChip } from "@/components/ui";

type LinkedRouteScreenProps = {
  activeHref: string;
  title: string;
  subtitle: string;
  icon: LucideIcon;
  scope: string;
  nextStep: string;
};

export function LinkedRouteScreen({
  activeHref,
  title,
  subtitle,
  icon: Icon,
  scope,
  nextStep,
}: LinkedRouteScreenProps) {
  return (
    <AppShell activeHref={activeHref}>
      <div className="space-y-6">
        <PageHeader
          title={title}
          subtitle={subtitle}
          chips={<StatusChip status="draft" label="Route shell" />}
        />

        <section className="grid gap-4 lg:grid-cols-2">
          <SectionCard title="Current scope">
            <div className="flex items-start gap-3 border-l-2 border-primary pl-4">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-card bg-primary-soft text-primary">
                <Icon className="h-5 w-5" aria-hidden="true" />
              </div>
              <p className="text-sm leading-6 text-text-secondary">{scope}</p>
            </div>
          </SectionCard>

          <SectionCard title="Next step">
            <p className="border-l-2 border-border pl-4 text-sm leading-6 text-text-secondary">
              {nextStep}
            </p>
          </SectionCard>
        </section>
      </div>
    </AppShell>
  );
}
