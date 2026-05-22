import { DriveScreen } from "@/features/drive/drive-screen";
import { parseDriveState } from "@/lib/drive-mock-data";

type DrivePageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function DrivePage({ searchParams }: DrivePageProps) {
  const params = await searchParams;
  const state = parseDriveState(params?.state);

  return <DriveScreen stateOverride={state} />;
}
