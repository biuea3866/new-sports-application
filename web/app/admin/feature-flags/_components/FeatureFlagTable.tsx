/**
 * S1 목록 화면 플래그 테이블 — 순수 프레젠테이션.
 * 행 클릭 시 onRowClick(key)만 호출하고, 실제 라우팅은 page.tsx(컨테이너)가 담당한다(no-logic-in-component).
 * 근거 티켓: FE-07-list-screen.md, 근거 설계: design-fe-web.md "S1 텍스트 와이어프레임".
 */
import Link from "next/link";
import type { FeatureFlagResponse } from "@/lib/admin/feature-flags/schemas";
import { StatusBadge } from "./StatusBadge";
import { TypeBadge } from "./TypeBadge";
import { StrategySummary } from "./StrategySummary";

interface FeatureFlagTableProps {
  flags: FeatureFlagResponse[];
  onRowClick: (key: string) => void;
}

export function FeatureFlagTable({ flags, onRowClick }: FeatureFlagTableProps): JSX.Element {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b bg-muted/50 text-left">
            <th className="px-4 py-3 font-medium">KEY</th>
            <th className="px-4 py-3 font-medium">종류</th>
            <th className="px-4 py-3 font-medium">상태</th>
            <th className="px-4 py-3 font-medium">전략</th>
            <th className="px-4 py-3 font-medium">수정</th>
          </tr>
        </thead>
        <tbody>
          {flags.map((flag) => (
            <tr
              key={flag.key}
              onClick={() => onRowClick(flag.key)}
              className="cursor-pointer border-b hover:bg-muted/25"
            >
              <td className="px-4 py-3 font-mono">{flag.key}</td>
              <td className="px-4 py-3">
                <TypeBadge type={flag.type} />
              </td>
              <td className="px-4 py-3">
                <StatusBadge status={flag.status} />
              </td>
              <td className="px-4 py-3">
                <StrategySummary strategy={flag.strategy} />
              </td>
              <td className="px-4 py-3">
                <Link
                  href={`/admin/feature-flags/${encodeURIComponent(flag.key)}`}
                  aria-label={`${flag.key} 수정`}
                  onClick={(event) => event.stopPropagation()}
                  className="text-primary hover:underline"
                >
                  수정 &gt;
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
