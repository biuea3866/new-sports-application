import Link from "next/link";

type DashboardCard = {
  href: string;
  title: string;
  body: string;
};

const CARDS: ReadonlyArray<DashboardCard> = [
  {
    href: "/admin/mcp/tokens",
    title: "MCP 토큰 관리",
    body: "운영자 토큰 발급·조회·폐기 및 비활성 토큰 재활성화.",
  },
  {
    href: "/admin/mcp/audit-logs",
    title: "감사 로그",
    body: "본인 토큰의 호출 이력을 시간·tool 단위로 조회합니다.",
  },
  {
    href: "/admin/mcp/docs",
    title: "MCP 사용 가이드",
    body: "Claude Desktop / ChatGPT / n8n 등 클라이언트별 설정 예시.",
  },
];

export default function AdminHome(): JSX.Element {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-gray-900">어드민 홈</h1>
        <p className="mt-1 text-sm text-gray-600">
          B2B MCP Server MVP — 토큰 관리·감사 로그·사용 가이드를 한 곳에서 운영하세요.
        </p>
      </header>
      <section
        aria-label="MCP 메뉴 카드"
        className="grid grid-cols-1 gap-4 md:grid-cols-3"
      >
        {CARDS.map((card) => (
          <Link
            key={card.href}
            href={card.href}
            className="rounded-lg border border-gray-200 p-4 hover:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <h2 className="text-base font-semibold text-gray-900">{card.title}</h2>
            <p className="mt-1 text-sm text-gray-600">{card.body}</p>
          </Link>
        ))}
      </section>
    </div>
  );
}
