import {
  AuditLogScreen,
  type AuditLogState,
} from "@/features/audit/audit-log-screen";

type AuditPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function AuditPage({ searchParams }: AuditPageProps) {
  const params = await searchParams;
  const state = parseAuditState(params?.state);
  const correlationId = firstParam(params?.correlationId);

  return (
    <AuditLogScreen
      correlationId={correlationId || undefined}
      stateOverride={state}
    />
  );
}

function parseAuditState(
  value: string | string[] | undefined,
): AuditLogState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  const validStates: AuditLogState[] = [
    "normal",
    "loading",
    "empty",
    "error",
    "permission-denied",
  ];

  if (validStates.includes(state as AuditLogState))
    return state as AuditLogState;
  return undefined;
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
