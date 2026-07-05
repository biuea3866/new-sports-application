/**
 * 플래그 상태(ACTIVE/ARCHIVED) 배지 — 순수 프레젠테이션.
 * 색은 FE-02 시맨틱 토큰만 사용한다(no-hardcoded-color). S1·S3 공유.
 * 근거 티켓: FE-05.
 */
import { cn } from "@/lib/utils";
import type { FeatureFlagStatus } from "@/lib/admin/feature-flags/schemas";

interface StatusBadgeProps {
  status: FeatureFlagStatus;
}

function assertNever(value: never): never {
  throw new Error(`처리되지 않은 status 타입입니다: ${JSON.stringify(value)}`);
}

function statusClassName(status: FeatureFlagStatus): string {
  switch (status) {
    case "ACTIVE":
      return "bg-success/15 text-success";
    case "ARCHIVED":
      return "bg-muted text-muted-foreground";
    default:
      return assertNever(status);
  }
}

export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold",
        statusClassName(status)
      )}
    >
      {status}
    </span>
  );
}
