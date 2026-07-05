"use client";

/**
 * S3 플래그 수정 화면 — description·strategy 수정, 상태 전이(아카이브/재활성).
 * 컨테이너: `useFeatureFlag(key)`로 프리필, 저장/아카이브/재활성 mutation.
 * ARCHIVED 상태면 전략·설명 입력을 비활성화하고 재활성만 노출(BE 409 규칙 UI 선반영).
 * `AdminLayout`이 ToastProvider를 아직 감싸지 않으므로(공유 파일 미변경) 이 화면 안에서만 감싼다(S2 선례).
 * 근거 티켓: `FE-09-edit-screen.md`, 근거 설계: `design-fe-web.md` "S3 와이어프레임 / 상태 전이 UI".
 */
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ToastProvider, useToast } from "@/components/ui/toast";
import { useFeatureFlag } from "@/lib/admin/feature-flags/hooks";
import { updateFeatureFlag, archiveFeatureFlag, activateFeatureFlag } from "@/lib/admin/feature-flags/api";
import type { FeatureFlagStrategy } from "@/lib/admin/feature-flags/schemas";

import { StatusBadge } from "../_components/StatusBadge";
import { TypeBadge } from "../_components/TypeBadge";
import { StrategyForm } from "../_components/StrategyForm";

const LIST_PATH = "/admin/feature-flags";

function toUserMessage(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback;
}

export default function FeatureFlagEditPage(): JSX.Element {
  return (
    <ToastProvider>
      <FeatureFlagEditPageContent />
    </ToastProvider>
  );
}

function FeatureFlagEditPageContent(): JSX.Element {
  const params = useParams<{ key: string }>();
  const flagKey = params.key;
  const { addToast } = useToast();

  const { data: flag, isLoading, error, refetch } = useFeatureFlag(flagKey);

  const [description, setDescription] = useState("");
  const [strategy, setStrategy] = useState<FeatureFlagStrategy | null>(null);
  const [isStrategyValid, setIsStrategyValid] = useState(true);

  const [isSaving, setIsSaving] = useState(false);
  const [isArchiving, setIsArchiving] = useState(false);
  const [isActivating, setIsActivating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (flag) {
      // BE는 description을 nullable로 반환할 수 있다 — 입력값은 항상 string이어야 하므로 빈 문자열로 대체한다.
      setDescription(flag.description ?? "");
      setStrategy(flag.strategy);
    }
  }, [flag]);

  async function handleSave(): Promise<void> {
    if (!flag || !strategy) return;
    setIsSaving(true);
    setActionError(null);
    try {
      await updateFeatureFlag(flag.key, { description, strategy });
      addToast({ title: "변경 사항이 저장됐습니다.", variant: "default" });
      refetch();
    } catch (err) {
      setActionError(toUserMessage(err, "저장 중 오류가 발생했습니다."));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleArchive(): Promise<void> {
    if (!flag) return;
    setIsArchiving(true);
    setActionError(null);
    try {
      await archiveFeatureFlag(flag.key);
      addToast({ title: "플래그가 아카이브됐습니다.", variant: "default" });
      refetch();
    } catch (err) {
      setActionError(toUserMessage(err, "아카이브 중 오류가 발생했습니다."));
    } finally {
      setIsArchiving(false);
    }
  }

  async function handleActivate(): Promise<void> {
    if (!flag) return;
    setIsActivating(true);
    setActionError(null);
    try {
      await activateFeatureFlag(flag.key);
      addToast({ title: "플래그가 재활성됐습니다.", variant: "default" });
      refetch();
    } catch (err) {
      setActionError(toUserMessage(err, "재활성 중 오류가 발생했습니다."));
    } finally {
      setIsActivating(false);
    }
  }

  if (isLoading) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-8">
        <p className="text-sm text-muted-foreground" aria-live="polite" aria-busy="true">
          불러오는 중...
        </p>
      </main>
    );
  }

  if (error || !flag) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-8 space-y-4">
        <div className="rounded-md border p-6 text-center space-y-4">
          <p className="text-sm text-muted-foreground">플래그를 찾을 수 없습니다.</p>
          <Link
            href={LIST_PATH}
            className="text-sm font-medium text-primary hover:underline"
          >
            목록으로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  const isArchived = flag.status === "ARCHIVED";

  return (
    <main className="max-w-2xl mx-auto px-4 py-8 space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link
            href={LIST_PATH}
            aria-label="목록으로 돌아가기"
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            ←
          </Link>
          <h1 className="text-xl font-semibold text-foreground">{flag.key}</h1>
          <StatusBadge status={flag.status} />
          <TypeBadge type={flag.type} />
        </div>
        <Link
          href={`${LIST_PATH}/${encodeURIComponent(flag.key)}/audit-logs`}
          className="text-sm font-medium text-primary hover:underline"
        >
          변경 이력 보기
        </Link>
      </div>

      {actionError && (
        <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
          {actionError}
        </div>
      )}

      <div className="space-y-2">
        <label htmlFor="flag-description" className="block text-sm font-medium text-foreground">
          설명
        </label>
        <Input
          id="flag-description"
          value={description}
          disabled={isArchived}
          onChange={(event) => setDescription(event.target.value)}
        />
      </div>

      <div className="border-t pt-4">
        <h2 className="mb-3 text-sm font-semibold text-foreground">평가 전략</h2>
        {strategy && (
          <StrategyForm
            value={strategy}
            onChange={setStrategy}
            flagType={flag.type}
            disabled={isArchived}
            onValidityChange={setIsStrategyValid}
          />
        )}
      </div>

      <div className="flex items-center justify-between border-t pt-4">
        {isArchived ? (
          <Button onClick={() => void handleActivate()} disabled={isActivating}>
            {isActivating ? "재활성 중..." : "재활성"}
          </Button>
        ) : (
          <>
            <Button
              variant="outline"
              className="border-destructive text-destructive hover:bg-destructive/10"
              onClick={() => void handleArchive()}
              disabled={isArchiving}
            >
              {isArchiving ? "아카이브 중..." : "아카이브"}
            </Button>
            <Button
              onClick={() => void handleSave()}
              disabled={isSaving || !isStrategyValid}
            >
              {isSaving ? "저장 중..." : "변경 저장"}
            </Button>
          </>
        )}
      </div>
    </main>
  );
}
