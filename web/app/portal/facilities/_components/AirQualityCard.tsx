import * as React from "react";
import { AirQualityBadge } from "@/components/ui/AirQualityBadge";
import type { AirQualityResponse } from "@/lib/portal/air-quality";

export type AirQualityCardStatus = "idle" | "loading" | "success" | "error";

export interface AirQualityCardProps {
  status: AirQualityCardStatus;
  data: AirQualityResponse | null;
}

const UNAVAILABLE_MESSAGE = "대기질 정보를 불러올 수 없습니다";
const LOADING_MESSAGE = "대기질 정보를 불러오는 중…";

/**
 * 시설 상세 대기질 카드 (프레젠테이션 전용, 데이터 페칭 없음).
 * status/data는 useAirQuality 훅(FE-06)이 반환하는 형태를 그대로 받는다.
 */
export function AirQualityCard({ status, data }: AirQualityCardProps) {
  if (status === "loading") {
    return (
      <p className="text-sm text-muted-foreground" aria-live="polite" aria-busy="true">
        {LOADING_MESSAGE}
      </p>
    );
  }

  if (status === "idle") {
    return null;
  }

  if (status === "error") {
    return (
      <p className="text-sm text-muted-foreground" role="alert">
        {UNAVAILABLE_MESSAGE}
      </p>
    );
  }

  if (!data || isAirQualityUnavailable(data)) {
    return (
      <p className="text-sm text-muted-foreground" role="alert">
        {UNAVAILABLE_MESSAGE}
      </p>
    );
  }

  const measuredTime = formatMeasuredTime(data.measuredAt);

  return (
    <div className="space-y-2 rounded-lg border p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">현재 대기질</span>
        <AirQualityBadge grade={data.representativeGrade} />
      </div>

      <dl className="space-y-1 text-sm">
        {data.pm10 !== null && (
          <div className="flex justify-between">
            <dt className="text-muted-foreground">미세먼지(PM10)</dt>
            <dd>{data.pm10} ㎍/㎥</dd>
          </div>
        )}
        {data.pm25 !== null && (
          <div className="flex justify-between">
            <dt className="text-muted-foreground">초미세먼지(PM2.5)</dt>
            <dd>{data.pm25} ㎍/㎥</dd>
          </div>
        )}
      </dl>

      {(data.stationName || measuredTime) && (
        <p className="text-xs text-muted-foreground">
          {[data.stationName ? `${data.stationName} 측정소` : null, measuredTime ? `${measuredTime} 기준` : null]
            .filter(Boolean)
            .join(" · ")}
        </p>
      )}
    </div>
  );
}

function isAirQualityUnavailable(data: AirQualityResponse): boolean {
  return data.representativeGrade === "UNKNOWN" || (data.pm10 === null && data.pm25 === null);
}

function formatMeasuredTime(measuredAt: string | null): string | null {
  if (!measuredAt) {
    return null;
  }
  const date = new Date(measuredAt);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  }).format(date);
}
