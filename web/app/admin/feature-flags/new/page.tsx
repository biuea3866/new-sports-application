"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ToastProvider, useToast } from "@/components/ui/toast";
import { createFeatureFlag } from "@/lib/admin/feature-flags/api";
import type { CreateFeatureFlagInput } from "@/lib/admin/feature-flags/schemas";
import { FeatureFlagCreateForm } from "./FeatureFlagCreateForm";

/**
 * S2 플래그 생성 화면 컨테이너 — `/admin/feature-flags/new`.
 * 제출 시 createFeatureFlag(FE-04)를 호출, 201이면 토스트 + 목록(S1)으로 이동한다.
 * `AdminLayout`이 ToastProvider를 아직 감싸지 않으므로(공유 파일 미변경) 이 화면 안에서만 감싼다.
 * 근거 티켓: FE-08-create-screen.md, 근거 설계: design-fe-web.md "S2 와이어프레임".
 */
export default function NewFeatureFlagPage(): JSX.Element {
  return (
    <ToastProvider>
      <NewFeatureFlagPageContent />
    </ToastProvider>
  );
}

function NewFeatureFlagPageContent(): JSX.Element {
  const router = useRouter();
  const { addToast } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  async function handleSubmit(input: CreateFeatureFlagInput): Promise<void> {
    setIsSubmitting(true);
    setServerError(null);
    try {
      await createFeatureFlag(input);
      addToast({ title: "생성됨", variant: "default" });
      router.push("/admin/feature-flags");
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "피처 플래그 생성에 실패했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleCancel(): void {
    router.push("/admin/feature-flags");
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-semibold text-foreground">플래그 추가</h1>
      <FeatureFlagCreateForm
        onSubmit={handleSubmit}
        onCancel={handleCancel}
        isSubmitting={isSubmitting}
        serverError={serverError}
      />
    </div>
  );
}
