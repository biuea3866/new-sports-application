"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import {
  CreateFeatureFlagInputSchema,
  FeatureFlagTypeSchema,
  type CreateFeatureFlagInput,
  type FeatureFlagStrategy,
  type FeatureFlagType,
} from "@/lib/admin/feature-flags/schemas";
import { Button } from "@/components/ui/button";
import { StrategyForm } from "@/app/admin/feature-flags/_components/StrategyForm";
import {
  createDefaultStrategyFor,
  strategyTypeOptionsFor,
} from "@/app/admin/feature-flags/_components/strategyFormState";

interface FeatureFlagCreateFormProps {
  onSubmit: (input: CreateFeatureFlagInput) => void | Promise<void>;
  onCancel: () => void;
  isSubmitting: boolean;
  serverError: string | null;
}

type FieldErrors = Partial<Record<"key" | "description", string>>;

const DEFAULT_TYPE: FeatureFlagType = "RELEASE";

/**
 * S2 플래그 생성 전용 폼 — key/type/description + StrategyForm(FE-06) 조합.
 * 수정 화면(FE-09)과 파일을 공유하지 않는다(생성 전용, Single Writer 회피).
 * 근거 티켓: FE-08-create-screen.md, 근거 설계: design-fe-web.md "S2 와이어프레임".
 */
export function FeatureFlagCreateForm({
  onSubmit,
  onCancel,
  isSubmitting,
  serverError,
}: FeatureFlagCreateFormProps): JSX.Element {
  const [key, setKey] = useState("");
  const [type, setType] = useState<FeatureFlagType>(DEFAULT_TYPE);
  const [description, setDescription] = useState("");
  const [strategy, setStrategy] = useState<FeatureFlagStrategy>(
    createDefaultStrategyFor(strategyTypeOptionsFor(DEFAULT_TYPE)[0] ?? "GLOBAL_TOGGLE")
  );
  const [isStrategyValid, setIsStrategyValid] = useState(true);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  function handleTypeChange(nextType: FeatureFlagType): void {
    setType(nextType);
    const options = strategyTypeOptionsFor(nextType);
    if (!options.includes(strategy.strategyType)) {
      setStrategy(createDefaultStrategyFor(options[0] ?? "GLOBAL_TOGGLE"));
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();

    const result = CreateFeatureFlagInputSchema.safeParse({ key, type, description, strategy });
    if (!result.success) {
      const nextErrors: FieldErrors = {};
      for (const issue of result.error.issues) {
        const field = issue.path[0];
        if (field === "key" || field === "description") {
          nextErrors[field] = issue.message;
        }
      }
      setFieldErrors(nextErrors);
      return;
    }

    setFieldErrors({});
    void onSubmit(result.data);
  }

  const isSubmitDisabled = isSubmitting || !isStrategyValid;

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-6">
      {serverError !== null && (
        <p
          role="alert"
          className="rounded-md border border-destructive p-3 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}

      <div>
        <label htmlFor="flag-key-input" className="block text-sm font-medium text-foreground">
          Key
        </label>
        <input
          id="flag-key-input"
          type="text"
          value={key}
          onChange={(event) => setKey(event.target.value)}
          placeholder="demo.feature.hello"
          className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          소문자와 점(.)으로 구성합니다. 예: demo.feature.hello
        </p>
        {fieldErrors.key !== undefined && (
          <p className="mt-1 text-xs text-destructive">{fieldErrors.key}</p>
        )}
      </div>

      <div>
        <label htmlFor="flag-type-select" className="block text-sm font-medium text-foreground">
          종류
        </label>
        <select
          id="flag-type-select"
          value={type}
          onChange={(event) => handleTypeChange(event.target.value as FeatureFlagType)}
          className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        >
          {FeatureFlagTypeSchema.options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label
          htmlFor="flag-description-input"
          className="block text-sm font-medium text-foreground"
        >
          설명
        </label>
        <input
          id="flag-description-input"
          type="text"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        />
        {fieldErrors.description !== undefined && (
          <p className="mt-1 text-xs text-destructive">{fieldErrors.description}</p>
        )}
      </div>

      <div className="space-y-4 border-t border-border pt-4">
        <h2 className="text-sm font-semibold text-foreground">평가 전략</h2>
        <StrategyForm
          value={strategy}
          onChange={setStrategy}
          flagType={type}
          onValidityChange={setIsStrategyValid}
        />
      </div>

      <div className="flex justify-end gap-3">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          취소
        </Button>
        <Button type="submit" disabled={isSubmitDisabled}>
          {isSubmitting ? "생성 중..." : "생성"}
        </Button>
      </div>
    </form>
  );
}
