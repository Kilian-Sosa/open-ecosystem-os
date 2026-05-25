import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type NotificationRecord = {
  notificationId: string;
  title: string;
  body: string;
  severity: "info" | "warning" | "danger";
  status: "unread" | "read";
  sourceType: string;
  sourceId: string;
  correlationId: string;
  createdAt: string;
  readAt: string | null;
};

export type NotificationListResponse = {
  notifications: NotificationRecord[];
};

export async function fetchNotifications(
  correlationId?: string,
): Promise<NotificationListResponse> {
  const params = new URLSearchParams();
  if (correlationId)
    params.set("correlationId", correlationId);

  const response = await fetch(
    `${API_BASE_URL}/api/notifications${params.size ? `?${params}` : ""}`,
    {
      headers: workspaceHeaders,
    },
  );

  if (!response.ok)
    throw new Error("Notifications could not be loaded");

  return response.json() as Promise<NotificationListResponse>;
}
