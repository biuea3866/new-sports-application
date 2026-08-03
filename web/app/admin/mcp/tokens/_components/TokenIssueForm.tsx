"use client";

import { useState, type FormEvent } from "react";
import { AVAILABLE_SCOPES, type AvailableScope, type IssueMcpTokenResponse } from "@/lib/admin/mcp/schemas";
import { DateField } from "@/components/ui/date-field";

interface TokenIssueFormProps {
  onIssued: (response: IssueMcpTokenResponse) => void;
}

export function TokenIssueForm({ onIssued }: TokenIssueFormProps): JSX.Element {
  const [name, setName] = useState("");
  const [selectedScopes, setSelectedScopes] = useState<Set<AvailableScope>>(new Set());
  const [includePii, setIncludePii] = useState(false);
  const [nonInteractive, setNonInteractive] = useState(false);
  const [expiresAt, setExpiresAt] = useState<string>("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function toggleScope(scope: AvailableScope): void {
    setSelectedScopes((prev) => {
      const next = new Set(prev);
      if (next.has(scope)) {
        next.delete(scope);
      } else {
        next.add(scope);
      }
      return next;
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setErrorMessage(null);

    const scopes: string[] = Array.from(selectedScopes);
    if (includePii) scopes.push("read:pii");
    if (nonInteractive) scopes.push("write:booking:any");

    if (name.trim() === "") {
      setErrorMessage("토큰 이름을 입력해 주세요.");
      return;
    }
    if (scopes.length === 0) {
      setErrorMessage("scope를 1개 이상 선택해 주세요.");
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch("/api/admin/mcp/tokens", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: name.trim(),
          scopes,
          expiresAt: expiresAt !== "" ? new Date(expiresAt).toISOString() : null,
        }),
      });
      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        setErrorMessage(body.message ?? "토큰 발급에 실패했습니다.");
        return;
      }
      const issued = (await response.json()) as IssueMcpTokenResponse;
      onIssued(issued);
      setName("");
      setSelectedScopes(new Set());
      setIncludePii(false);
      setNonInteractive(false);
      setExpiresAt("");
    } catch {
      setErrorMessage("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <form onSubmit={(e) => void handleSubmit(e)} aria-label="MCP 토큰 발급 폼" className="space-y-4">
      <div>
        <label htmlFor="token-name" className="block text-sm font-medium text-foreground">
          토큰 이름 <span aria-hidden="true" className="text-destructive">*</span>
        </label>
        <input
          id="token-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="예: n8n 자동화 토큰"
          required
          className="mt-1 w-full rounded border border-input bg-background px-3 py-2 text-sm text-foreground focus:border-ring focus:outline-none focus:ring-1 focus:ring-ring"
          aria-required="true"
        />
      </div>

      <fieldset>
        <legend className="text-sm font-medium text-foreground">
          Scope <span aria-hidden="true" className="text-destructive">*</span>
        </legend>
        <div className="mt-2 grid grid-cols-2 gap-2">
          {AVAILABLE_SCOPES.map((scope) => (
            <label key={scope} className="flex cursor-pointer items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={selectedScopes.has(scope)}
                onChange={() => toggleScope(scope)}
                aria-label={`scope ${scope} 선택`}
                className="h-4 w-4 rounded border-input text-primary focus:ring-ring"
              />
              <span className="font-mono text-xs text-foreground">{scope}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <div>
        <label htmlFor="token-expires-at" className="block text-sm font-medium text-foreground">
          만료일 <span className="text-xs text-muted-foreground">(선택 — 미입력 시 무기한)</span>
        </label>
        <DateField
          id="token-expires-at"
          value={expiresAt}
          onChange={setExpiresAt}
          className="mt-1"
          aria-label="토큰 만료일 (선택)"
        />
      </div>

      <fieldset>
        <legend className="text-sm font-medium text-foreground">추가 권한</legend>
        <div className="mt-2 space-y-2">
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              id="pii-toggle"
              type="checkbox"
              checked={includePii}
              onChange={(e) => setIncludePii(e.target.checked)}
              aria-label="PII 조회 권한 포함"
              className="h-4 w-4 rounded border-input text-primary focus:ring-ring"
            />
            <span>PII 조회 포함</span>
            <span className="text-xs text-muted-foreground">(read:pii)</span>
          </label>
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              id="non-interactive-toggle"
              type="checkbox"
              checked={nonInteractive}
              onChange={(e) => setNonInteractive(e.target.checked)}
              aria-label="비대화형 자동화 권한 포함"
              className="h-4 w-4 rounded border-input text-primary focus:ring-ring"
            />
            <span>비대화형 자동화</span>
            <span className="text-xs text-muted-foreground">(write:booking:any)</span>
          </label>
        </div>
      </fieldset>

      {errorMessage !== null && (
        <p role="alert" className="text-sm text-destructive">
          {errorMessage}
        </p>
      )}

      <button
        type="submit"
        disabled={isLoading}
        className="rounded bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        aria-label="MCP 토큰 발급"
      >
        {isLoading ? "발급 중..." : "토큰 발급"}
      </button>
    </form>
  );
}
