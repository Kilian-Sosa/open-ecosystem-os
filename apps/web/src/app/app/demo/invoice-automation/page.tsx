import {
  InvoiceAutomationScreen,
  type DemoInvoiceState,
} from "@/features/demo/invoice-automation-screen";

type DemoInvoicePageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function DemoInvoicePage({
  searchParams,
}: DemoInvoicePageProps) {
  const params = await searchParams;
  const state = parseDemoInvoiceState(params?.state);

  return <InvoiceAutomationScreen stateOverride={state} />;
}

function parseDemoInvoiceState(
  value: string | string[] | undefined,
): DemoInvoiceState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  const validStates: DemoInvoiceState[] = [
    "normal",
    "loading",
    "empty",
    "error",
    "permission-denied",
  ];

  if (validStates.includes(state as DemoInvoiceState))
    return state as DemoInvoiceState;
  return undefined;
}
