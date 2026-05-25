import {
  SearchScreen,
  type SearchScreenState,
} from "@/features/search/search-screen";

type SearchPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;
  const state = parseSearchState(params?.state);
  const query = firstParam(params?.q);

  return <SearchScreen initialQuery={query} stateOverride={state} />;
}

function parseSearchState(
  value: string | string[] | undefined,
): SearchScreenState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  const validStates: SearchScreenState[] = [
    "normal",
    "loading",
    "empty",
    "error",
    "permission-denied",
  ];

  if (validStates.includes(state as SearchScreenState))
    return state as SearchScreenState;
  return undefined;
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
