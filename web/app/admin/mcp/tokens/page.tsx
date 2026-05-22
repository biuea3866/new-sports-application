"use client";

import { useState, useEffect, useCallback } from "react";
import { TokenIssueForm } from "./_components/TokenIssueForm";
import { TokenList } from "./_components/TokenList";
import { PlainTokenModal } from "./_components/PlainTokenModal";
import type { IssueMcpTokenResponse, ListMcpTokensResponse, McpTokenSummary } from "@/lib/admin/mcp/schemas";

export default function McpTokensPage(): JSX.Element {
  const [tokens, setTokens] = useState<McpTokenSummary[]>([]);
  const [isLoadingTokens, setIsLoadingTokens] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [revokeError, setRevokeError] = useState<string | null>(null);
  const [issuedToken, setIssuedToken] = useState<IssueMcpTokenResponse | null>(null);

  const fetchTokens = useCallback(async () => {
    setIsLoadingTokens(true);
    setLoadError(null);
    try {
      const response = await fetch("/api/admin/mcp/tokens");
      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        setLoadError(body.message ?? "토큰 목록 조회에 실패했습니다.");
        return;
      }
      const data = (await response.json()) as ListMcpTokensResponse;
      setTokens(data.tokens);
    } catch {
      setLoadError("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsLoadingTokens(false);
    }
  }, []);

  useEffect(() => {
    void fetchTokens();
  }, [fetchTokens]);

  function handleIssued(response: IssueMcpTokenResponse): void {
    setIssuedToken(response);
    void fetchTokens();
  }

  async function handleRevoke(tokenId: number): Promise<void> {
    setRevokeError(null);
    try {
      const response = await fetch(`/api/admin/mcp/tokens/${tokenId}`, {
        method: "DELETE",
      });
      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        setRevokeError(body.message ?? "토큰 폐기에 실패했습니다.");
        return;
      }
      await fetchTokens();
    } catch {
      setRevokeError("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  function handleModalClose(): void {
    setIssuedToken(null);
  }

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl font-semibold text-gray-900">MCP 토큰 관리</h1>
        <p className="mt-1 text-sm text-gray-600">
          운영자 본인의 MCP 토큰을 발급·조회·폐기합니다.
        </p>
      </header>

      <section aria-labelledby="issue-section-title" className="rounded-lg border border-gray-200 bg-white p-6">
        <h2 id="issue-section-title" className="mb-4 text-base font-semibold text-gray-900">
          새 토큰 발급
        </h2>
        <TokenIssueForm onIssued={handleIssued} />
      </section>

      <section aria-labelledby="list-section-title" className="rounded-lg border border-gray-200 bg-white p-6">
        <h2 id="list-section-title" className="mb-4 text-base font-semibold text-gray-900">
          발급된 토큰 목록
        </h2>
        {isLoadingTokens && <p className="text-sm text-gray-500">로딩 중...</p>}
        {!isLoadingTokens && loadError !== null && (
          <p role="alert" className="text-sm text-red-600">{loadError}</p>
        )}
        {revokeError !== null && (
          <p role="alert" className="text-sm text-red-600">{revokeError}</p>
        )}
        {!isLoadingTokens && loadError === null && (
          <TokenList
            tokens={tokens}
            onRevoke={(id) => void handleRevoke(id)}
          />
        )}
      </section>

      {issuedToken !== null && (
        <PlainTokenModal
          open={true}
          tokenName={issuedToken.name}
          plainToken={issuedToken.plainToken}
          onClose={handleModalClose}
        />
      )}
    </div>
  );
}
