import { MediaScreen } from "@/features/media/media-screen";
import { parseMediaState } from "@/lib/media-mock-data";

type MediaPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function MediaPage({ searchParams }: MediaPageProps) {
  const params = await searchParams;
  const state = parseMediaState(params?.state);
  const jobId = firstParam(params?.jobId);
  const fileId = firstParam(params?.fileId);

  return (
    <MediaScreen
      initialFileId={fileId || undefined}
      initialJobId={jobId || undefined}
      stateOverride={state}
    />
  );
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
