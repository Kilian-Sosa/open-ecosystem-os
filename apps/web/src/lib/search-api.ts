import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type SearchResult = {
  id: string;
  sourceType: string;
  sourceId: string;
  title: string;
  summary: string;
  resourceHref: string;
  correlationId: string;
  status: "pending" | "indexing" | "indexed" | "failed" | string;
  metadata: Record<string, string | number | boolean | null>;
  createdAt: string;
};

export type SearchResponse = {
  query: string;
  backend: string;
  results: SearchResult[];
};

export async function fetchSearchResults(
  query: string,
): Promise<SearchResponse> {
  const params = new URLSearchParams({ q: query });
  const response = await fetch(`${API_BASE_URL}/api/search?${params}`, {
    headers: workspaceHeaders,
  });

  if (!response.ok)
    throw new Error("Search results could not be loaded");

  return response.json() as Promise<SearchResponse>;
}
