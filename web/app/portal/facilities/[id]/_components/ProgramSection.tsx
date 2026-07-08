"use client";

/**
 * ProgramSection(W-PG) — 시설상품 목록 + 등록.
 */
import * as React from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { usePrograms, createProgram } from "@/lib/portal/usePrograms";
import type { CreateProgramInput } from "@/lib/portal/types";
import { ProgramCard } from "./ProgramCard";
import { ProgramFormDialog } from "./ProgramFormDialog";

export interface ProgramSectionProps {
  facilityId: string;
}

export function ProgramSection({ facilityId }: ProgramSectionProps) {
  const { data, status, error, refetch } = usePrograms(facilityId);
  const { addToast } = useToast();
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [dialogError, setDialogError] = React.useState<string | null>(null);

  async function handleCreate(input: CreateProgramInput) {
    setSubmitting(true);
    setDialogError(null);
    try {
      await createProgram(facilityId, input);
      addToast({ title: "시설상품이 등록됐습니다.", variant: "default" });
      setDialogOpen(false);
      refetch();
    } catch (err) {
      setDialogError(err instanceof Error ? err.message : "시설상품 등록에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  if (status === "loading") {
    return (
      <p aria-busy="true" className="text-sm text-muted-foreground">
        시설상품을 불러오는 중...
      </p>
    );
  }

  if (status === "error") {
    return (
      <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
        {error ?? "시설상품을 불러오지 못했습니다."}
      </div>
    );
  }

  const programs = data ?? [];

  return (
    <section aria-label="시설상품 관리" className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-muted-foreground">시설상품</h2>
        <Button
          type="button"
          size="sm"
          onClick={() => setDialogOpen(true)}
          aria-label="시설상품 등록"
        >
          + 상품 등록
        </Button>
      </div>

      {programs.length === 0 ? (
        <p className="text-sm text-muted-foreground">등록된 상품이 없어요</p>
      ) : (
        <ul className="space-y-2" aria-label="시설상품 목록">
          {programs.map((program) => (
            <li key={program.id}>
              <ProgramCard program={program} />
            </li>
          ))}
        </ul>
      )}

      <ProgramFormDialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) setDialogError(null);
        }}
        onSubmit={(input) => void handleCreate(input)}
        submitting={submitting}
        error={dialogError}
      />
    </section>
  );
}
