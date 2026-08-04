/**
 * 감사 로그 변경 유형(CREATED/UPDATED/ARCHIVED/ACTIVATED) 배지 — 순수 프레젠테이션.
 * TypeBadge와 동일한 중립 accent 라벨 패턴 — S5(감사 로그) 화면 전용이라 accent 재사용에 충돌 없음.
 * 라벨은 featureFlagChangeType.ts(단일 출처)를 통해 한글로 렌더한다 — 계약 밖 값은 원문 폴백.
 * 근거 티켓: FE-05.
 */
import { cn } from "@/lib/utils";
import { featureFlagChangeTypeLabel } from "@/lib/admin/feature-flags/featureFlagChangeType";

interface ChangeTypeBadgeProps {
  changeType: string;
}

export function ChangeTypeBadge({ changeType }: ChangeTypeBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full bg-accent px-2.5 py-0.5 text-xs font-semibold text-accent-foreground"
      )}
    >
      {featureFlagChangeTypeLabel(changeType)}
    </span>
  );
}
