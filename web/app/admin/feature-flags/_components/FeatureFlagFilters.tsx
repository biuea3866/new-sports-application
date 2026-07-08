/**
 * S1 목록 화면 status/type 필터 — 순수 프레젠테이션(controlled).
 * 필터 값은 page.tsx(컨테이너)가 지역 useState로 소유하고, 이 컴포넌트는 props만 받아 렌더한다(no-logic-in-component).
 * 근거 티켓: FE-07-list-screen.md, 근거 설계: design-fe-web.md "S1 텍스트 와이어프레임".
 */
import type { FeatureFlagStatus, FeatureFlagType } from "@/lib/admin/feature-flags/schemas";

interface FeatureFlagFiltersProps {
  status: FeatureFlagStatus | undefined;
  type: FeatureFlagType | undefined;
  onStatusChange: (status: FeatureFlagStatus | undefined) => void;
  onTypeChange: (type: FeatureFlagType | undefined) => void;
}

const STATUS_OPTIONS: { value: FeatureFlagStatus | ""; label: string }[] = [
  { value: "", label: "전체" },
  { value: "ACTIVE", label: "ACTIVE" },
  { value: "ARCHIVED", label: "ARCHIVED" },
];

const TYPE_OPTIONS: { value: FeatureFlagType | ""; label: string }[] = [
  { value: "", label: "전체" },
  { value: "RELEASE", label: "RELEASE" },
  { value: "OPERATIONAL", label: "OPERATIONAL" },
  { value: "EXPERIMENT", label: "EXPERIMENT" },
  { value: "ENTITLEMENT", label: "ENTITLEMENT" },
];

const SELECT_CLASS_NAME =
  "rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground";

export function FeatureFlagFilters({
  status,
  type,
  onStatusChange,
  onTypeChange,
}: FeatureFlagFiltersProps): JSX.Element {
  return (
    <div className="flex flex-wrap items-center gap-4">
      <label className="flex items-center gap-2 text-sm text-foreground">
        상태
        <select
          aria-label="상태"
          value={status ?? ""}
          onChange={(event) =>
            onStatusChange(
              event.target.value === "" ? undefined : (event.target.value as FeatureFlagStatus)
            )
          }
          className={SELECT_CLASS_NAME}
        >
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>

      <label className="flex items-center gap-2 text-sm text-foreground">
        종류
        <select
          aria-label="종류"
          value={type ?? ""}
          onChange={(event) =>
            onTypeChange(
              event.target.value === "" ? undefined : (event.target.value as FeatureFlagType)
            )
          }
          className={SELECT_CLASS_NAME}
        >
          {TYPE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
