import { getMyDashboardSummary } from "@/lib/portal/dashboard";
import { getB2BRoles } from "@/lib/server/auth";
import { PortalApiError } from "@/lib/portal/error";
import type { DashboardSummary } from "@/lib/portal/types";

// ─── 카드 컴포넌트 ────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string;
  value: number;
  unit?: string;
}

function StatCard({ label, value, unit }: StatCardProps) {
  return (
    <div className="rounded-lg border bg-card p-4 flex flex-col gap-1">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold tabular-nums">
        {value.toLocaleString("ko-KR")}
        {unit && <span className="text-sm font-normal text-muted-foreground ml-1">{unit}</span>}
      </p>
    </div>
  );
}

// ─── 섹션 컴포넌트 ────────────────────────────────────────────────────────────

function FacilitySection({ data }: { data: NonNullable<DashboardSummary["facilities"]> }) {
  return (
    <section aria-labelledby="facility-section-heading" className="space-y-3">
      <h2 id="facility-section-heading" className="text-lg font-semibold">
        시설 / 슬롯
      </h2>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <StatCard label="내 시설 수" value={data.count} unit="개" />
        <StatCard label="오늘 활성 슬롯" value={data.activeSlotsToday} unit="개" />
      </div>
    </section>
  );
}

function EventSection({ data }: { data: NonNullable<DashboardSummary["events"]> }) {
  return (
    <section aria-labelledby="event-section-heading" className="space-y-3">
      <h2 id="event-section-heading" className="text-lg font-semibold">
        경기
      </h2>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <StatCard label="예정" value={data.scheduled} unit="건" />
        <StatCard label="오픈" value={data.open} unit="건" />
        <StatCard label="종료" value={data.closed} unit="건" />
        <StatCard label="전체 좌석" value={data.totalSeats} unit="석" />
        <StatCard label="판매 좌석" value={data.soldSeats} unit="석" />
      </div>
    </section>
  );
}

function ProductSection({ data }: { data: NonNullable<DashboardSummary["products"]> }) {
  return (
    <section aria-labelledby="product-section-heading" className="space-y-3">
      <h2 id="product-section-heading" className="text-lg font-semibold">
        상품
      </h2>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <StatCard label="판매 중" value={data.active} unit="개" />
        <StatCard label="품절" value={data.outOfStock} unit="개" />
      </div>
    </section>
  );
}

// ─── 페이지 ───────────────────────────────────────────────────────────────────

export const metadata = {
  title: "대시보드 — 사업자 포털",
};

export default async function PortalDashboardPage() {
  const b2bRoles = getB2BRoles();

  let summary: DashboardSummary | null = null;
  let errorMessage: string | null = null;

  try {
    summary = await getMyDashboardSummary();
  } catch (err) {
    if (err instanceof PortalApiError) {
      errorMessage = err.userMessage;
    } else {
      errorMessage = "대시보드 정보를 불러오지 못했습니다.";
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-6 py-8 space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">대시보드</h1>
        <p className="text-sm text-muted-foreground mt-1">내 사업 현황을 한눈에 확인합니다.</p>
      </div>

      {errorMessage !== null && (
        <div
          role="alert"
          className="rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive"
        >
          {errorMessage}
        </div>
      )}

      {summary !== null && (
        <div className="space-y-8">
          {b2bRoles.includes("FACILITY_OWNER") && summary.facilities !== null && (
            <FacilitySection data={summary.facilities} />
          )}
          {b2bRoles.includes("EVENT_HOST") && summary.events !== null && (
            <EventSection data={summary.events} />
          )}
          {b2bRoles.includes("GOODS_SELLER") && summary.products !== null && (
            <ProductSection data={summary.products} />
          )}
          {summary.facilities === null &&
            summary.events === null &&
            summary.products === null && (
              <p className="text-sm text-muted-foreground">
                표시할 데이터가 없습니다.
              </p>
            )}
        </div>
      )}
    </div>
  );
}
