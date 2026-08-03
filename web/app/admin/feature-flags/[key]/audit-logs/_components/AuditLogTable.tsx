/**
 * S5 변경 이력 테이블 — 순수 프레젠테이션.
 * before→after는 StrategySummary(FE-05)로 사람이 읽는 요약으로 변환한다.
 * before가 없으면(최초 CREATED) "이전 값 없음"으로 표시한다 — 값 가공 없이 뷰 분기만 담당(no-logic-in-component).
 * BE는 NON_NULL 직렬화라 before가 null일 때 키 자체가 생략되므로 null·undefined를 함께 다룬다.
 * 변경자는 actorDisplayName(닉네임)을 주 정보로 보여주고 actorUserId를 보조 식별자로 병기한다 —
 * 닉네임은 중복·미설정("닉네임 미설정")이 가능해 이름만으로는 행위자를 특정할 수 없다. 감사 로그는
 * 소셜 화면과 달리 행위자 식별의 정확성이 표현의 깔끔함보다 중요하다(내부 PK 단독 노출 결함은
 * "누가 바꿨는지" 표시 자체가 없던 문제였지, 식별자 병기가 문제였던 적은 없다).
 * 근거 티켓: FE-10-audit-log-screen.md, 근거 설계: design-fe-web.md "S5 와이어프레임".
 */
import { ChangeTypeBadge } from "@/app/admin/feature-flags/_components/ChangeTypeBadge";
import { StrategySummary } from "@/app/admin/feature-flags/_components/StrategySummary";
import { formatDateTime } from "@/lib/admin/datetime";
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
                {formatDateTime(log.occurredAt)}
              </td>
              <td className="px-4 py-3">
                <ChangeTypeBadge changeType={log.changeType} />
              </td>
              <td className="px-4 py-3">
                <span>{log.actorDisplayName}</span>
                <span className="ml-1 text-xs text-muted-foreground">#{log.actorUserId}</span>
              </td>
              <td className="px-4 py-3">
                {log.before === null || log.before === undefined ? (
                  <span className="text-muted-foreground">이전 값 없음</span>
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
