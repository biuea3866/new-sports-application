import { redirect } from "next/navigation";
import Link from "next/link";
import { ToastProvider } from "@/components/ui/toast";
import { getSessionInfo, getB2BRoles } from "@/lib/server/auth";
import type { B2BRole } from "@/lib/server/auth";

interface NavItem {
  href: string;
  label: string;
  requiredRole: B2BRole | null;
}

const NAV_ITEMS: NavItem[] = [
  { href: "/portal", label: "대시보드", requiredRole: null },
  { href: "/portal/facilities", label: "시설", requiredRole: "FACILITY_OWNER" },
  { href: "/portal/slots", label: "슬롯", requiredRole: "FACILITY_OWNER" },
  { href: "/portal/events", label: "경기", requiredRole: "EVENT_HOST" },
  { href: "/portal/products", label: "상품", requiredRole: "GOODS_SELLER" },
];

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  const session = getSessionInfo();

  if (!session) {
    redirect("/login");
  }

  const b2bRoles = getB2BRoles();
  if (b2bRoles.length === 0) {
    redirect("/");
  }

  const visibleNavItems = NAV_ITEMS.filter(
    (item) => item.requiredRole === null || b2bRoles.includes(item.requiredRole)
  );

  return (
    <ToastProvider>
      <div className="flex min-h-screen">
        <nav
          className="w-56 shrink-0 border-r bg-muted/30 flex flex-col py-6 px-4 gap-1"
          aria-label="포털 네비게이션"
        >
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3 px-2">
            사업자 포털
          </p>
          {visibleNavItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
              aria-label={item.label}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <main className="flex-1 overflow-auto">{children}</main>
      </div>
    </ToastProvider>
  );
}
