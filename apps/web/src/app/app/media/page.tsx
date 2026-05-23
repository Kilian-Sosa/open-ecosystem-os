import { MediaScreen } from "@/features/media/media-screen";
import { parseMediaState } from "@/lib/media-mock-data";

type MediaPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function MediaPage({ searchParams }: MediaPageProps) {
  const params = await searchParams;
  const state = parseMediaState(params?.state);

  return <MediaScreen stateOverride={state} />;
}
