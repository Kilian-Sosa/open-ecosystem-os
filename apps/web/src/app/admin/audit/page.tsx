import { Activity } from "lucide-react";

import { LinkedRouteScreen } from "@/features/navigation/linked-route-screen";

export default function AuditPage() {
  return (
    <LinkedRouteScreen
      activeHref="/admin/audit"
      title="Audit logs"
      subtitle="Inspect workspace audit records and automation traceability."
      icon={Activity}
      scope="This shell keeps the admin audit route live while the MVP audit table is queried from backend contracts."
      nextStep="Connect audit records with filters for actor, resource, outcome, correlation ID, and event source."
    />
  );
}
