"use client";

import { useState } from "react";
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

const STATUS_CLASS: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-800",
  SUSPENDED: "bg-yellow-100 text-yellow-800",
  REVOKED: "bg-red-100 text-red-800",
};

function formatDate(iso: string | null): string {
  if (iso === null) return "—";
  return new Date(iso).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

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
    return (
      <p className="text-sm text-gray-500">발급된 토큰이 없습니다.</p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm" aria-label="MCP 토큰 목록">
        <thead>
          <tr className="border-b border-gray-200 text-left text-xs font-medium uppercase text-gray-500">
            <th className="pb-2 pr-4" scope="col">이름</th>
            <th className="pb-2 pr-4" scope="col">상태</th>
            <th className="pb-2 pr-4" scope="col">만료일</th>
            <th className="pb-2 pr-4" scope="col">마지막 사용</th>
            <th className="pb-2 pr-4" scope="col">발급일</th>
            <th className="pb-2" scope="col">작업</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {tokens.map((token) => (
            <tr key={token.tokenId} className="py-2">
              <td className="py-2 pr-4 font-medium text-gray-900">{token.name}</td>
              <td className="py-2 pr-4">
                <span
                  className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASS[token.status] ?? ""}`}
                >
                  {STATUS_LABEL[token.status] ?? token.status}
                </span>
              </td>
              <td className="py-2 pr-4 text-gray-600">{formatDate(token.expiresAt)}</td>
              <td className="py-2 pr-4 text-gray-600">{formatDate(token.lastUsedAt)}</td>
              <td className="py-2 pr-4 text-gray-600">{formatDate(token.createdAt)}</td>
              <td className="py-2">
                {token.status !== "REVOKED" && confirmingId !== token.tokenId && (
                  <button
                    type="button"
                    onClick={() => handleRevokeClick(token.tokenId)}
                    className="rounded border border-red-300 px-2 py-1 text-xs text-red-600 hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-400"
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
                      className="rounded bg-red-600 px-2 py-1 text-xs text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-400"
                      aria-label={`${token.name} 토큰 폐기 확인`}
                    >
                      확인
                    </button>
                    <button
                      type="button"
                      onClick={handleCancel}
                      className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400"
                      aria-label="폐기 취소"
                    >
                      취소
                    </button>
                  </span>
                )}
                {token.status === "REVOKED" && (
                  <span className="text-xs text-gray-400">폐기됨</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
