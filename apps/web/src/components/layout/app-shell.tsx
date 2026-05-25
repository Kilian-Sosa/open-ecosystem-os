import type { LucideIcon } from "lucide-react";
import {
  Activity,
  Bell,
  Boxes,
  Bot,
  FileText,
  Folder,
  HelpCircle,
  Home,
  Menu,
  Search,
  Settings,
  ShieldCheck,
  UploadCloud,
  Workflow,
} from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { SearchInput } from "@/components/ui/search-input";
import { ThemeSwitcher } from "@/components/theme";
import { cn } from "@/lib/cn";

type NavigationItem = {
  label: string;
  href: string;
  icon: LucideIcon;
};

const primaryNavigation: NavigationItem[] = [
  { label: "Dashboard", href: "/app/dashboard", icon: Home },
  { label: "Invoice demo", href: "/app/demo/invoice-automation", icon: Bot },
  { label: "Drive", href: "/app/drive", icon: Folder },
  { label: "Media and OCR", href: "/app/media", icon: UploadCloud },
  { label: "Flows", href: "/app/flows", icon: Workflow },
  { label: "Search", href: "/app/search", icon: Search },
  { label: "Notifications", href: "/app/notifications", icon: Bell },
];

const systemNavigation: NavigationItem[] = [
  { label: "Audit logs", href: "/admin/audit", icon: Activity },
  { label: "System status", href: "/admin/system-status", icon: ShieldCheck },
  { label: "Settings", href: "/app/settings", icon: Settings },
];

const mobileNavigation: NavigationItem[] = [
  { label: "Home", href: "/app/dashboard", icon: Home },
  { label: "Demo", href: "/app/demo/invoice-automation", icon: Bot },
  { label: "Search", href: "/app/search", icon: Search },
  { label: "Files", href: "/app/media", icon: FileText },
  { label: "Settings", href: "/app/settings", icon: Settings },
];

type AppShellProps = {
  activeHref: string;
  children: ReactNode;
  inspector?: ReactNode;
};

export function AppShell({ activeHref, children, inspector }: AppShellProps) {
  return (
    <div className="min-h-screen bg-background text-text-primary">
      <div className="hidden min-h-screen md:flex">
        <DesktopSidebar activeHref={activeHref} />
        <div className="flex min-w-0 flex-1 flex-col">
          <TopCommandBar />
          <div className="flex min-w-0 flex-1">
            <main className="min-w-0 flex-1 px-6 py-6 lg:px-7">
              <div className="mx-auto max-w-app">{children}</div>
            </main>
            {inspector ? (
              <div className="hidden shrink-0 xl:block">{inspector}</div>
            ) : null}
          </div>
        </div>
      </div>

      <div className="min-h-screen pb-20 md:hidden">
        <MobileTopBar />
        <main className="px-4 py-5">{children}</main>
        <MobileBottomNav activeHref={activeHref} />
      </div>
    </div>
  );
}

function DesktopSidebar({ activeHref }: { activeHref: string }) {
  return (
    <aside className="flex w-[var(--layout-sidebar-width)] shrink-0 flex-col border-r border-border bg-surface">
      <div className="flex h-20 items-center gap-3 border-b border-border px-5">
        <div className="flex h-9 w-9 items-center justify-center rounded-card bg-primary-soft text-primary">
          <Boxes className="h-5 w-5" aria-hidden="true" />
        </div>
        <div>
          <p className="text-sm font-semibold text-text-primary">
            Open Ecosystem OS
          </p>
          <p className="text-xs text-text-secondary">Workspace</p>
        </div>
      </div>

      <nav
        className="flex-1 space-y-7 overflow-y-auto px-4 py-5"
        aria-label="Workspace navigation"
      >
        <NavigationGroup items={primaryNavigation} activeHref={activeHref} />
        <NavigationGroup
          title="System"
          items={systemNavigation}
          activeHref={activeHref}
        />
      </nav>

      <div className="border-t border-border p-4">
        <div className="rounded-card border border-border bg-surface-muted p-4">
          <div className="flex items-center justify-between text-xs font-medium text-text-secondary">
            <span>Storage</span>
            <span>42%</span>
          </div>
          <div className="mt-3 h-2 rounded-full bg-surface">
            <div className="h-full w-[42%] rounded-full bg-primary" />
          </div>
          <p className="mt-3 text-xs text-text-secondary">
            860 GB of 2 TB used
          </p>
        </div>
      </div>
    </aside>
  );
}

function NavigationGroup({
  title,
  items,
  activeHref,
}: {
  title?: string;
  items: NavigationItem[];
  activeHref: string;
}) {
  return (
    <div className="space-y-2">
      {title ? (
        <p className="px-3 text-xs font-medium uppercase tracking-normal text-text-muted">
          {title}
        </p>
      ) : null}
      {items.map((item) => (
        <NavigationLink
          key={item.href}
          item={item}
          active={item.href === activeHref}
        />
      ))}
    </div>
  );
}

function NavigationLink({
  item,
  active,
}: {
  item: NavigationItem;
  active: boolean;
}) {
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex min-h-10 items-center gap-3 rounded-card px-3 text-sm font-medium text-text-secondary hover:bg-surface-muted hover:text-text-primary",
        active && "bg-primary-soft text-primary",
      )}
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      <span>{item.label}</span>
    </Link>
  );
}

function TopCommandBar() {
  return (
    <header className="sticky top-0 z-20 flex h-20 items-center justify-between gap-4 border-b border-border bg-surface/95 px-6 backdrop-blur lg:px-7">
      <SearchInput
        aria-label="Search workspace"
        className="max-w-2xl flex-1"
        placeholder="Search files, flows, OCR text, and activity..."
        shortcutLabel="Ctrl K"
      />
      <div className="flex items-center gap-2">
        <ThemeSwitcher />
        <IconButton label="Open notifications" icon={Bell} />
        <IconButton label="Open help" icon={HelpCircle} />
        <div className="ml-2 flex items-center gap-3 border-l border-border pl-4">
          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary-soft text-sm font-semibold text-primary">
            AD
          </div>
          <div className="hidden text-sm lg:block">
            <p className="font-medium text-text-primary">Admin</p>
            <p className="text-xs text-text-secondary">Workspace Admin</p>
          </div>
        </div>
      </div>
    </header>
  );
}

function MobileTopBar() {
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-surface/95 backdrop-blur">
      <div className="flex h-16 items-center justify-between px-4">
        <div className="flex min-w-0 items-center gap-3">
          <IconButton label="Open navigation menu" icon={Menu} />
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-card bg-primary-soft text-primary">
            <Boxes className="h-4 w-4" aria-hidden="true" />
          </div>
          <span className="truncate text-sm font-semibold text-text-primary">
            Open Ecosystem OS
          </span>
        </div>
        <IconButton label="Open notifications" icon={Bell} />
      </div>
      <div className="flex items-center gap-2 px-4 pb-4">
        <SearchInput
          aria-label="Search workspace"
          className="min-w-0 flex-1"
          placeholder="Search workspace..."
        />
        <ThemeSwitcher />
      </div>
    </header>
  );
}

function MobileBottomNav({ activeHref }: { activeHref: string }) {
  return (
    <nav
      aria-label="Mobile workspace navigation"
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface px-3 py-2"
    >
      <div className="grid grid-cols-5 gap-1">
        {mobileNavigation.map((item) => {
          const Icon = item.icon;
          const active = item.href === activeHref;

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-h-14 flex-col items-center justify-center gap-1 rounded-card text-xs font-medium text-text-secondary",
                active && "bg-primary-soft text-primary",
              )}
            >
              <Icon className="h-5 w-5" aria-hidden="true" />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

function IconButton({
  label,
  icon: Icon,
}: {
  label: string;
  icon: LucideIcon;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-card border border-transparent text-text-secondary hover:border-border hover:bg-surface-muted hover:text-text-primary"
    >
      <Icon className="h-5 w-5" aria-hidden="true" />
    </button>
  );
}
