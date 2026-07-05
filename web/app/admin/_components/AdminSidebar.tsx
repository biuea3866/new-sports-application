import Link from "next/link";

type NavItem = {
  href: string;
  label: string;
  description: string;
};

const MCP_NAV: ReadonlyArray<NavItem> = [
  {
    href: "/admin/mcp/tokens",
    label: "MCP 토큰",
    description: "발급·조회·폐기",
  },
  {
    href: "/admin/mcp/audit-logs",
    label: "감사 로그",
    description: "토큰별 호출 이력",
  },
  {
    href: "/admin/mcp/docs",
    label: "MCP 사용 가이드",
    description: "클라이언트별 설정",
  },
  {
    href: "/admin/mcp/usage-analytics",
    label: "MCP 사용 분석",
    description: "호출·에러율·P95 대시보드",
  },
  {
    href: "/admin/mcp/anomalies",
    label: "이상 패턴",
    description: "비정상 호출 패턴 히스토리",
  },
];

const FEATURE_FLAG_NAV: ReadonlyArray<NavItem> = [
  {
    href: "/admin/feature-flags",
    label: "피처 플래그",
    description: "플래그 목록·전략·감사",
  },
];

export function AdminSidebar(): JSX.Element {
  return (
    <aside
      role="complementary"
      aria-label="어드민 사이드바"
      className="w-64 border-r border-gray-200 bg-white p-4"
    >
      <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-gray-500">
        MCP
      </h2>
      <nav>
        <ul className="space-y-1">
          {MCP_NAV.map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className="block rounded-md px-3 py-2 text-sm hover:bg-gray-50 focus:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <span className="block font-medium">{item.label}</span>
                <span className="block text-xs text-gray-500">{item.description}</span>
              </Link>
            </li>
          ))}
        </ul>
        <h2 className="mb-4 mt-6 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
          피처 플래그
        </h2>
        <ul className="space-y-1">
          {FEATURE_FLAG_NAV.map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className="block rounded-md px-3 py-2 text-sm text-foreground hover:bg-accent focus:bg-accent focus:outline-none focus:ring-2 focus:ring-ring"
              >
                <span className="block font-medium">{item.label}</span>
                <span className="block text-xs text-muted-foreground">{item.description}</span>
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  );
}
