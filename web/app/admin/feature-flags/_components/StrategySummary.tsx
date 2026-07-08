/**
 * strategy를 사람이 읽는 요약 문자열로 렌더하는 순수 프레젠테이션 컴포넌트.
 * 변환 로직은 strategyLabel.ts에 위임한다(no-logic-in-component). S1·S3 공유.
 * 근거 티켓: FE-05.
 */
import type { FeatureFlagStrategy } from "@/lib/admin/feature-flags/schemas";
import { getStrategyLabel } from "./strategyLabel";

interface StrategySummaryProps {
  strategy: FeatureFlagStrategy;
}

export function StrategySummary({ strategy }: StrategySummaryProps) {
  return <span>{getStrategyLabel(strategy)}</span>;
}
