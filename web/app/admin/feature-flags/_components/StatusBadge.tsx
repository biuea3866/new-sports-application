/**
 * 플래그 상태(ACTIVE/ARCHIVED) 배지 — 순수 프레젠테이션.
 * 색은 FE-02 시맨틱 토큰만 사용한다(no-hardcoded-color). S1·S3 공유.
 * 라벨은 featureFlagStatus.ts(단일 출처)를 통해 한글로 렌더한다 — 계약 밖 값은 원문 폴백.
 * 근거 티켓: FE-05.
 */
import { cn } from "@/lib/utils";
import { featureFlagStatusLabel } from "@/lib/admin/feature-flags/featureFlagStatus";

interface StatusBadgeProps {
  status: string;
}

const KNOWN_STATUS_CLASS_NAMES: Record<string, string> = {
  ACTIVE: "bg-success/15 text-success",
  ARCHIVED: "bg-muted text-muted-foreground",
};

const UNKNOWN_STATUS_CLASS_NAME = "bg-muted text-muted-foreground";

function statusClassName(status: string): string {
  return KNOWN_STATUS_CLASS_NAMES[status] ?? UNKNOWN_STATUS_CLASS_NAME;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold",
        statusClassName(status)
      )}
    >
      {featureFlagStatusLabel(status)}
    </span>
  );
}
