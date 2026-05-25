import { DriveScreen } from "@/features/drive/drive-screen";
import { parseDriveState } from "@/lib/drive-mock-data";

type DrivePageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function DrivePage({ searchParams }: DrivePageProps) {
  const params = await searchParams;
  const state = parseDriveState(params?.state);
  const fileId = firstParam(params?.fileId);

  return (
    <DriveScreen initialFileId={fileId || undefined} stateOverride={state} />
  );
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
