import { Bell } from "lucide-react";

import { LinkedRouteScreen } from "@/features/navigation/linked-route-screen";

export default function NotificationsPage() {
  return (
    <LinkedRouteScreen
      activeHref="/app/notifications"
      title="Notifications"
      subtitle="Review workspace notifications from workflows and system activity."
      icon={Bell}
      scope="This shell keeps the navigation route live while the MVP notification inbox is shaped around persisted workflow notifications."
      nextStep="Connect this page to the notifications table, unread filters, and read-state actions."
    />
  );
}
