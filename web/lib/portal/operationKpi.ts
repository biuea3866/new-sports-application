/**
 * 운영 통합 KPI 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/operator/dashboard/kpi BFF 엔드포인트만 호출한다.
 */

export interface FacilityKpi {
  utilizationRate: number;
  noShowRate: number;
  topFacilityIds: number[];
}

export interface GoodsKpi {
  dailyRevenueTotal: number;
  inventoryTurnoverRate: number;
  outOfStockSkuCount: number;
}

export interface TicketKpi {
  totalSoldCount: number;
  refundRate: number;
  complimentaryCount: number;
}

export interface OperationKpiResponse {
  ownerUserId: number;
  facility: FacilityKpi;
  goods: GoodsKpi;
  ticket: TicketKpi;
}

export interface FetchKpiParams {
  from: string;
  to: string;
}

/** 운영 통합 KPI 조회 */
export async function fetchOperationKpi(params: FetchKpiParams): Promise<OperationKpiResponse> {
  const query = new URLSearchParams({ from: params.from, to: params.to });
  const res = await fetch(`/api/operator/dashboard/kpi?${query.toString()}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `KPI 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<OperationKpiResponse>;
}
