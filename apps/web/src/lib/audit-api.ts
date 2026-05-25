import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type AuditRecord = {
  auditId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  actorId: string;
  correlationId: string;
  outcome: "success" | "failed" | string;
  attributes: Record<string, string>;
  occurredAt: string;
};

export type AuditRecordListResponse = {
  records: AuditRecord[];
};

export async function fetchAuditRecords(
  correlationId?: string,
): Promise<AuditRecordListResponse> {
  const params = new URLSearchParams();
  if (correlationId)
    params.set("correlationId", correlationId);

  const response = await fetch(
    `${API_BASE_URL}/api/audit/records${params.size ? `?${params}` : ""}`,
    {
      headers: workspaceHeaders,
    },
  );

  if (!response.ok)
    throw new Error("Audit records could not be loaded");

  return response.json() as Promise<AuditRecordListResponse>;
}
