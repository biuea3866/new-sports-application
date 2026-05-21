"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { FacilityForm } from "@/app/portal/facilities/_components/FacilityForm";
import { useToast } from "@/components/ui/toast";
import type { FacilityFormValues } from "@/app/portal/facilities/facility-form-schema";

export default function NewFacilityPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [serverError, setServerError] = React.useState<string | null>(null);

  async function handleSubmit(values: FacilityFormValues) {
    setIsSubmitting(true);
    setServerError(null);

    try {
      const response = await fetch("/api/portal/facilities", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });

      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        setServerError(body.message ?? "등록 중 오류가 발생했습니다.");
        return;
      }

      addToast({ title: "시설이 등록됐습니다.", variant: "default" });
      router.push("/portal/facilities");
    } catch {
      setServerError("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="max-w-2xl mx-auto px-4 py-8 space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">새 시설 등록</h1>

      {serverError && (
        <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
          {serverError}
        </div>
      )}

      <FacilityForm
        mode="create"
        isSubmitting={isSubmitting}
        onSubmit={(v) => { void handleSubmit(v); }}
        onCancel={() => router.push("/portal/facilities")}
      />
    </main>
  );
}
