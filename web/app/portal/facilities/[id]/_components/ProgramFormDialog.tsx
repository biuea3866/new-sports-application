"use client";

/**
 * ProgramFormDialog — 시설상품 등록 다이얼로그 (이름·설명·가격·정원·소요시간).
 */
import * as React from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { CreateProgramInputSchema } from "@/lib/portal/schemas";
import type { CreateProgramInput } from "@/lib/portal/types";

export interface ProgramFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (input: CreateProgramInput) => void;
  submitting: boolean;
  error: string | null;
}

interface FormState {
  name: string;
  description: string;
  price: string;
  capacity: string;
  durationMinutes: string;
}

const INITIAL_FORM: FormState = {
  name: "",
  description: "",
  price: "",
  capacity: "",
  durationMinutes: "",
};

export function ProgramFormDialog({
  open,
  onOpenChange,
  onSubmit,
  submitting,
  error,
}: ProgramFormDialogProps) {
  const [form, setForm] = React.useState<FormState>(INITIAL_FORM);
  const [fieldErrors, setFieldErrors] = React.useState<Partial<Record<keyof FormState, string>>>(
    {}
  );

  React.useEffect(() => {
    if (!open) {
      setForm(INITIAL_FORM);
      setFieldErrors({});
    }
  }, [open]);

  function handleChange(field: keyof FormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    const candidate = {
      name: form.name,
      description: form.description || undefined,
      price: Number(form.price),
      capacity: Number(form.capacity),
      durationMinutes: Number(form.durationMinutes),
    };

    const parsed = CreateProgramInputSchema.safeParse(candidate);
    if (!parsed.success) {
      const nextErrors: Partial<Record<keyof FormState, string>> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0];
        if (typeof key === "string" && key in INITIAL_FORM) {
          const field = key as keyof FormState;
          if (!nextErrors[field]) {
            nextErrors[field] = issue.message;
          }
        }
      }
      setFieldErrors(nextErrors);
      return;
    }

    setFieldErrors({});
    onSubmit(parsed.data);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent aria-labelledby="program-dialog-title">
        <DialogHeader>
          <DialogTitle id="program-dialog-title">시설상품 등록</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} noValidate aria-label="시설상품 등록 폼" className="space-y-3">
          <div className="space-y-1">
            <label htmlFor="program-name" className="text-sm font-medium">
              이름 <span aria-hidden="true" className="text-destructive">*</span>
            </label>
            <Input
              id="program-name"
              value={form.name}
              onChange={(e) => handleChange("name", e.target.value)}
              aria-required="true"
              aria-invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && (
              <p role="alert" className="text-xs text-destructive">
                {fieldErrors.name}
              </p>
            )}
          </div>

          <div className="space-y-1">
            <label htmlFor="program-description" className="text-sm font-medium">
              설명
            </label>
            <Input
              id="program-description"
              value={form.description}
              onChange={(e) => handleChange("description", e.target.value)}
            />
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div className="space-y-1">
              <label htmlFor="program-price" className="text-sm font-medium">
                가격
              </label>
              <Input
                id="program-price"
                type="number"
                min={0}
                value={form.price}
                onChange={(e) => handleChange("price", e.target.value)}
                aria-invalid={!!fieldErrors.price}
              />
              {fieldErrors.price && (
                <p role="alert" className="text-xs text-destructive">
                  {fieldErrors.price}
                </p>
              )}
            </div>
            <div className="space-y-1">
              <label htmlFor="program-capacity" className="text-sm font-medium">
                정원
              </label>
              <Input
                id="program-capacity"
                type="number"
                min={1}
                value={form.capacity}
                onChange={(e) => handleChange("capacity", e.target.value)}
                aria-invalid={!!fieldErrors.capacity}
              />
              {fieldErrors.capacity && (
                <p role="alert" className="text-xs text-destructive">
                  {fieldErrors.capacity}
                </p>
              )}
            </div>
            <div className="space-y-1">
              <label htmlFor="program-duration" className="text-sm font-medium">
                소요(분)
              </label>
              <Input
                id="program-duration"
                type="number"
                min={1}
                value={form.durationMinutes}
                onChange={(e) => handleChange("durationMinutes", e.target.value)}
                aria-invalid={!!fieldErrors.durationMinutes}
              />
              {fieldErrors.durationMinutes && (
                <p role="alert" className="text-xs text-destructive">
                  {fieldErrors.durationMinutes}
                </p>
              )}
            </div>
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={submitting}
            >
              취소
            </Button>
            <Button type="submit" disabled={submitting} aria-busy={submitting}>
              {submitting ? "저장 중..." : "저장"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
