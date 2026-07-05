/**
 * 감사 로그 변경 유형(CREATED/UPDATED/ARCHIVED/ACTIVATED) 배지 — 순수 프레젠테이션.
 * TypeBadge와 동일한 중립 accent 라벨 패턴 — S5(감사 로그) 화면 전용이라 accent 재사용에 충돌 없음.
 * 근거 티켓: FE-05.
 */
import { cn } from "@/lib/utils";
import type { ChangeType } from "@/lib/admin/feature-flags/schemas";

interface ChangeTypeBadgeProps {
  changeType: ChangeType;
}

export function ChangeTypeBadge({ changeType }: ChangeTypeBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full bg-accent px-2.5 py-0.5 text-xs font-semibold text-accent-foreground"
      )}
    >
      {changeType}
    </span>
  );
}
