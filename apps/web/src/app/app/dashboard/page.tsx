import { DashboardScreen } from "@/features/dashboard/dashboard-screen";
import {
  dashboardMockData,
  parseDashboardState,
} from "@/lib/dashboard-mock-data";

type DashboardPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function DashboardPage({
  searchParams,
}: DashboardPageProps) {
  const params = await searchParams;
  const state = parseDashboardState(params?.state);

  return <DashboardScreen data={dashboardMockData} state={state} />;
}
