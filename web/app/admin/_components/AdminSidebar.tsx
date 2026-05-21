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
];

export function AdminSidebar(): JSX.Element {
  return (
    <aside
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
      </nav>
    </aside>
  );
}
