/**
 * 플래그 타입(RELEASE/OPERATIONAL/EXPERIMENT/ENTITLEMENT) 배지 — 순수 프레젠테이션.
 * 색 남용 없이 라벨로만 구분하는 토스 절제 패턴 — 전부 중립 accent 토큰. S1·S3 공유.
 * 근거 티켓: FE-05.
 */
import { cn } from "@/lib/utils";
import type { FeatureFlagType } from "@/lib/admin/feature-flags/schemas";

interface TypeBadgeProps {
  type: FeatureFlagType;
}

export function TypeBadge({ type }: TypeBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full bg-accent px-2.5 py-0.5 text-xs font-semibold text-accent-foreground"
      )}
    >
      {type}
    </span>
  );
}
