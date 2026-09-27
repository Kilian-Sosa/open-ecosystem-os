import { MediaScreen } from "@/features/media/media-screen";

type MediaPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function MediaPage({ searchParams }: MediaPageProps) {
  const params = await searchParams;
  const jobId = firstParam(params?.jobId);
  const fileId = firstParam(params?.fileId);

  return (
    <MediaScreen
      initialFileId={fileId || undefined}
      initialJobId={jobId || undefined}
    />
  );
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
