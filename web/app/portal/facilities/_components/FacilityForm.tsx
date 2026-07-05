"use client";

import * as React from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SidoSelect } from "@/components/ui/SidoSelect";
import { useFacilityForm, FacilityTypeEnum } from "@/app/portal/facilities/_hooks/useFacilityForm";
import type { FacilityFormValues } from "@/app/portal/facilities/facility-form-schema";

const FACILITY_TYPE_LABELS: Record<string, string> = {
  INDOOR: "실내",
  OUTDOOR: "야외",
  MIXED: "복합",
};

interface FacilityFormProps {
  mode: "create" | "edit";
  defaultValues?: Partial<FacilityFormValues>;
  isSubmitting: boolean;
  onSubmit: (values: FacilityFormValues) => void | Promise<void>;
  onCancel: () => void;
}

export function FacilityForm({
  mode,
  defaultValues,
  isSubmitting,
  onSubmit,
  onCancel,
}: FacilityFormProps) {
  const { values, errors, handleChange, validate } = useFacilityForm({ defaultValues });

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!validate()) return;
    void onSubmit(values);
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={mode === "create" ? "시설 등록 폼" : "시설 수정 폼"} className="space-y-4">
      {/* 코드 — 수정 모드에서는 읽기 전용 */}
      {mode === "create" && (
        <div className="space-y-1">
          <label htmlFor="facility-code" className="text-sm font-medium">
            시설 코드 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="facility-code"
            type="text"
            value={values.code}
            onChange={(e) => handleChange("code", e.target.value)}
            placeholder="예: GWANG-01"
            aria-required="true"
            aria-describedby={errors.code ? "facility-code-error" : undefined}
            aria-invalid={!!errors.code}
          />
          {errors.code && (
            <p id="facility-code-error" role="alert" className="text-xs text-destructive">
              {errors.code}
            </p>
          )}
        </div>
      )}

      <div className="space-y-1">
        <label htmlFor="facility-name" className="text-sm font-medium">
          시설명 <span aria-hidden="true" className="text-destructive">*</span>
        </label>
        <Input
          id="facility-name"
          type="text"
          value={values.name}
          onChange={(e) => handleChange("name", e.target.value)}
          placeholder="예: 광진 실내 체육관"
          aria-required="true"
          aria-describedby={errors.name ? "facility-name-error" : undefined}
          aria-invalid={!!errors.name}
        />
        {errors.name && (
          <p id="facility-name-error" role="alert" className="text-xs text-destructive">
            {errors.name}
          </p>
        )}
      </div>

      <fieldset className="space-y-2 rounded-md border border-input p-3">
        <legend className="px-1 text-sm font-medium">지역</legend>
        <SidoSelect
          id="facility-sido"
          label="시/도"
          value={values.sido ?? ""}
          onChange={(sidoCode) => handleChange("sido", sidoCode)}
        />
        <p className="text-xs text-muted-foreground">미선택 시 주소로 자동 판별됩니다.</p>
      </fieldset>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <label htmlFor="facility-gu" className="text-sm font-medium">
            구 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="facility-gu"
            type="text"
            value={values.gu}
            onChange={(e) => handleChange("gu", e.target.value)}
            placeholder="예: 광진구"
            aria-required="true"
            aria-describedby={errors.gu ? "facility-gu-error" : undefined}
            aria-invalid={!!errors.gu}
          />
          {errors.gu && (
            <p id="facility-gu-error" role="alert" className="text-xs text-destructive">
              {errors.gu}
            </p>
          )}
        </div>

        <div className="space-y-1">
          <label htmlFor="facility-type" className="text-sm font-medium">
            유형 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <select
            id="facility-type"
            value={values.type}
            onChange={(e) => handleChange("type", e.target.value)}
            aria-required="true"
            aria-describedby={errors.type ? "facility-type-error" : undefined}
            aria-invalid={!!errors.type}
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {FacilityTypeEnum.map((t) => (
              <option key={t} value={t}>
                {FACILITY_TYPE_LABELS[t] ?? t}
              </option>
            ))}
          </select>
          {errors.type && (
            <p id="facility-type-error" role="alert" className="text-xs text-destructive">
              {errors.type}
            </p>
          )}
        </div>
      </div>

      <div className="space-y-1">
        <label htmlFor="facility-address" className="text-sm font-medium">
          주소 <span aria-hidden="true" className="text-destructive">*</span>
        </label>
        <Input
          id="facility-address"
          type="text"
          value={values.address}
          onChange={(e) => handleChange("address", e.target.value)}
          placeholder="예: 서울특별시 광진구 능동로 1"
          aria-required="true"
          aria-describedby={errors.address ? "facility-address-error" : undefined}
          aria-invalid={!!errors.address}
        />
        {errors.address && (
          <p id="facility-address-error" role="alert" className="text-xs text-destructive">
            {errors.address}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor="facility-location" className="text-sm font-medium">
          위치 좌표 (위도,경도) <span aria-hidden="true" className="text-destructive">*</span>
        </label>
        <Input
          id="facility-location"
          type="text"
          value={values.location}
          onChange={(e) => handleChange("location", e.target.value)}
          placeholder="예: 37.5512,127.0699"
          aria-required="true"
          aria-describedby={errors.location ? "facility-location-error" : undefined}
          aria-invalid={!!errors.location}
        />
        {errors.location && (
          <p id="facility-location-error" role="alert" className="text-xs text-destructive">
            {errors.location}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor="facility-tel" className="text-sm font-medium">
          전화번호 <span aria-hidden="true" className="text-destructive">*</span>
        </label>
        <Input
          id="facility-tel"
          type="tel"
          value={values.tel}
          onChange={(e) => handleChange("tel", e.target.value)}
          placeholder="예: 02-1234-5678"
          aria-required="true"
          aria-describedby={errors.tel ? "facility-tel-error" : undefined}
          aria-invalid={!!errors.tel}
        />
        {errors.tel && (
          <p id="facility-tel-error" role="alert" className="text-xs text-destructive">
            {errors.tel}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor="facility-homepage" className="text-sm font-medium">
          홈페이지
        </label>
        <Input
          id="facility-homepage"
          type="url"
          value={values.homePage ?? ""}
          onChange={(e) => handleChange("homePage", e.target.value)}
          placeholder="예: https://example.com"
        />
      </div>

      <div className="flex gap-6">
        <fieldset>
          <legend className="text-sm font-medium mb-1">주차</legend>
          <div className="flex items-center gap-2">
            <input
              id="facility-parking-yes"
              type="radio"
              name="parking"
              checked={values.parking === true}
              onChange={() => handleChange("parking", true)}
              className="h-4 w-4 border-input"
            />
            <label htmlFor="facility-parking-yes" className="text-sm">가능</label>
            <input
              id="facility-parking-no"
              type="radio"
              name="parking"
              checked={values.parking === false}
              onChange={() => handleChange("parking", false)}
              className="h-4 w-4 border-input ml-3"
            />
            <label htmlFor="facility-parking-no" className="text-sm">불가</label>
          </div>
        </fieldset>

        <fieldset>
          <legend className="text-sm font-medium mb-1">교육 여부</legend>
          <div className="flex items-center gap-2">
            <input
              id="facility-edu-yes"
              type="radio"
              name="eduYn"
              checked={values.eduYn === true}
              onChange={() => handleChange("eduYn", true)}
              className="h-4 w-4 border-input"
            />
            <label htmlFor="facility-edu-yes" className="text-sm">예</label>
            <input
              id="facility-edu-no"
              type="radio"
              name="eduYn"
              checked={values.eduYn === false}
              onChange={() => handleChange("eduYn", false)}
              className="h-4 w-4 border-input ml-3"
            />
            <label htmlFor="facility-edu-no" className="text-sm">아니오</label>
          </div>
        </fieldset>
      </div>

      <div className="space-y-1">
        <label htmlFor="facility-meta" className="text-sm font-medium">
          메타 정보 (선택)
        </label>
        <textarea
          id="facility-meta"
          value={values.meta ?? ""}
          onChange={(e) => handleChange("meta", e.target.value)}
          placeholder="추가 정보를 입력해 주세요."
          rows={3}
          className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none"
        />
      </div>

      <div className="flex gap-3 pt-2">
        <Button type="submit" disabled={isSubmitting} aria-label={mode === "create" ? "시설 등록" : "시설 수정 저장"}>
          {isSubmitting ? "저장 중..." : mode === "create" ? "등록" : "저장"}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={isSubmitting}
          aria-label="취소"
        >
          취소
        </Button>
      </div>
    </form>
  );
}
