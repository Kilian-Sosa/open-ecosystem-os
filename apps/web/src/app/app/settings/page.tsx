import { Settings } from "lucide-react";

import { LinkedRouteScreen } from "@/features/navigation/linked-route-screen";

export default function SettingsPage() {
  return (
    <LinkedRouteScreen
      activeHref="/app/settings"
      title="Settings"
      subtitle="Manage workspace preferences and platform configuration."
      icon={Settings}
      scope="This shell keeps the settings route available from desktop and mobile navigation."
      nextStep="Add workspace profile, storage, security, and integration settings as those backend contracts land."
    />
  );
}
