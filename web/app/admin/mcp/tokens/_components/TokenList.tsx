"use client";

import { useState } from "react";
import { formatDateOnly } from "@/lib/admin/datetime";
import type { McpTokenSummary } from "@/lib/admin/mcp/schemas";

interface TokenListProps {
  tokens: McpTokenSummary[];
  onRevoke: (tokenId: number) => void;
}

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "활성",
  SUSPENDED: "정지",
  REVOKED: "폐기",
};

/** 상태 배지 색은 시맨틱 토큰만 사용한다 — 라이트/다크 두 모드에서 모두 판독 가능해야 한다. */
const STATUS_CLASS: Record<string, string> = {
  ACTIVE: "bg-success/15 text-success",
  SUSPENDED: "bg-warning/15 text-warning",
  REVOKED: "bg-destructive/15 text-destructive",
};

export function TokenList({ tokens, onRevoke }: TokenListProps): JSX.Element {
  const [confirmingId, setConfirmingId] = useState<number | null>(null);

  function handleRevokeClick(tokenId: number): void {
    setConfirmingId(tokenId);
  }

  function handleConfirm(tokenId: number): void {
    setConfirmingId(null);
    onRevoke(tokenId);
  }

  function handleCancel(): void {
    setConfirmingId(null);
  }

  if (tokens.length === 0) {
    return <p className="text-sm text-muted-foreground">발급된 토큰이 없습니다.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm" aria-label="MCP 토큰 목록">
        <thead>
          <tr className="border-b border-border text-left text-xs font-medium uppercase text-muted-foreground">
            <th className="pb-2 pr-4" scope="col">이름</th>
            <th className="pb-2 pr-4" scope="col">상태</th>
            <th className="pb-2 pr-4" scope="col">만료일</th>
            <th className="pb-2 pr-4" scope="col">마지막 사용</th>
            <th className="pb-2 pr-4" scope="col">발급일</th>
            <th className="pb-2" scope="col">작업</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {tokens.map((token) => (
            <tr key={token.tokenId} className="py-2">
              <td className="py-2 pr-4 font-medium text-foreground">{token.name}</td>
              <td className="py-2 pr-4">
                <span
                  className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASS[token.status] ?? ""}`}
                >
                  {STATUS_LABEL[token.status] ?? token.status}
                </span>
              </td>
              <td className="py-2 pr-4 text-muted-foreground">{formatDateOnly(token.expiresAt)}</td>
              <td className="py-2 pr-4 text-muted-foreground">{formatDateOnly(token.lastUsedAt)}</td>
              <td className="py-2 pr-4 text-muted-foreground">{formatDateOnly(token.createdAt)}</td>
              <td className="py-2">
                {token.status !== "REVOKED" && confirmingId !== token.tokenId && (
                  <button
                    type="button"
                    onClick={() => handleRevokeClick(token.tokenId)}
                    className="rounded border border-destructive/40 px-2 py-1 text-xs text-destructive hover:bg-destructive/10 focus:outline-none focus:ring-2 focus:ring-destructive"
                    aria-label={`${token.name} 토큰 폐기`}
                  >
                    폐기
                  </button>
                )}
                {confirmingId === token.tokenId && (
                  <span className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => handleConfirm(token.tokenId)}
                      className="rounded bg-destructive px-2 py-1 text-xs text-destructive-foreground hover:bg-destructive/90 focus:outline-none focus:ring-2 focus:ring-destructive"
                      aria-label={`${token.name} 토큰 폐기 확인`}
                    >
                      확인
                    </button>
                    <button
                      type="button"
                      onClick={handleCancel}
                      className="rounded border border-border px-2 py-1 text-xs text-muted-foreground hover:bg-accent focus:outline-none focus:ring-2 focus:ring-ring"
                      aria-label="폐기 취소"
                    >
                      취소
                    </button>
                  </span>
                )}
                {token.status === "REVOKED" && (
                  <span className="text-xs text-muted-foreground">폐기됨</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
