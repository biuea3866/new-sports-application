/**
 * S5 변경 이력 테이블 — 순수 프레젠테이션.
 * before→after는 StrategySummary(FE-05)로 사람이 읽는 요약으로 변환한다.
 * before가 null(CREATED)이면 "(없음)"으로 표시한다 — 값 가공 없이 뷰 분기만 담당(no-logic-in-component).
 * 근거 티켓: FE-10-audit-log-screen.md, 근거 설계: design-fe-web.md "S5 와이어프레임".
 */
import { ChangeTypeBadge } from "@/app/admin/feature-flags/_components/ChangeTypeBadge";
import { StrategySummary } from "@/app/admin/feature-flags/_components/StrategySummary";
import type { FeatureFlagAuditLogResponse } from "@/lib/admin/feature-flags/schemas";

interface AuditLogTableProps {
  logs: FeatureFlagAuditLogResponse[];
}

export function AuditLogTable({ logs }: AuditLogTableProps): JSX.Element {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b bg-muted/50 text-left">
            <th className="px-4 py-3 font-medium">시각</th>
            <th className="px-4 py-3 font-medium">변경</th>
            <th className="px-4 py-3 font-medium">변경자</th>
            <th className="px-4 py-3 font-medium">이전 → 이후</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log, index) => (
            <tr key={`${log.occurredAt}-${index}`} className="border-b">
              <td className="px-4 py-3 text-muted-foreground">
                {new Date(log.occurredAt).toLocaleString("ko-KR")}
              </td>
              <td className="px-4 py-3">
                <ChangeTypeBadge changeType={log.changeType} />
              </td>
              <td className="px-4 py-3">#{log.actorUserId}</td>
              <td className="px-4 py-3">
                {log.before === null ? (
                  <span className="text-muted-foreground">(없음)</span>
                ) : (
                  <StrategySummary strategy={log.before.strategy} />
                )}
                {" → "}
                <StrategySummary strategy={log.after.strategy} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
