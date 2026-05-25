import { FlowsScreen } from "@/features/flows/flows-screen";
import { parseFlowsState } from "@/lib/flows-mock-data";

type FlowsPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function FlowsPage({ searchParams }: FlowsPageProps) {
  const params = await searchParams;
  const state = parseFlowsState(params?.state);

  return <FlowsScreen stateOverride={state} />;
}
