import {
  NotificationCenterScreen,
  type NotificationCenterState,
} from "@/features/notifications/notification-center-screen";

type NotificationsPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function NotificationsPage({
  searchParams,
}: NotificationsPageProps) {
  const params = await searchParams;
  const state = parseNotificationState(params?.state);
  const correlationId = firstParam(params?.correlationId);

  return (
    <NotificationCenterScreen
      correlationId={correlationId || undefined}
      stateOverride={state}
    />
  );
}

function parseNotificationState(
  value: string | string[] | undefined,
): NotificationCenterState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  const validStates: NotificationCenterState[] = [
    "normal",
    "loading",
    "empty",
    "error",
    "permission-denied",
  ];

  if (validStates.includes(state as NotificationCenterState))
    return state as NotificationCenterState;
  return undefined;
}

function firstParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
