import { FlowsScreen } from "@/features/flows/flows-screen";
import { parseFlowsState } from "@/lib/flows-mock-data";

type FlowsPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function FlowsPage({ searchParams }: FlowsPageProps) {
  const params = await searchParams;
  const state = parseFlowsState(params?.state);
  const executionId = firstParam(params?.executionId);
  const workflowId = firstParam(params?.workflowId);
  const correlationId = firstParam(params?.correlationId);

  return (
    <FlowsScreen
      initialCorrelationId={correlationId || undefined}
      initialExecutionId={executionId || undefined}
      initialWorkflowId={workflowId || undefined}
      stateOverride={state}
    />
  );
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value))
    return value[0] ?? "";
  return value ?? "";
}
