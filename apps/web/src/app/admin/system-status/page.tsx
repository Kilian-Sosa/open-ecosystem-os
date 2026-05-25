import { ShieldCheck } from "lucide-react";

import { LinkedRouteScreen } from "@/features/navigation/linked-route-screen";

export default function SystemStatusPage() {
  return (
    <LinkedRouteScreen
      activeHref="/admin/system-status"
      title="System status"
      subtitle="Monitor core service health for the local workspace stack."
      icon={ShieldCheck}
      scope="This shell keeps the system route live while health and readiness data remains available from service endpoints."
      nextStep="Connect API, worker, queue, object storage, search, and database health checks into one operator view."
    />
  );
}
