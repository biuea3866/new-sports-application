"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/components/ui/toast";
import { productFormSchema } from "../product-form-schema";
import type { MyProduct } from "@/lib/portal/types";

export default function NewProductPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<"name" | "description" | "price", string>>>(
    {}
  );

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);

    const raw = {
      name: (data.get("name") as string | null) ?? "",
      description: (data.get("description") as string | null) ?? "",
      price: Number((data.get("price") as string | null) ?? ""),
    };

    const parsed = productFormSchema.safeParse(raw);
    if (!parsed.success) {
      const fieldErrors: Partial<Record<"name" | "description" | "price", string>> = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as "name" | "description" | "price" | undefined;
        if (field) fieldErrors[field] = issue.message;
      }
      setErrors(fieldErrors);
      return;
    }

    setErrors({});
    setIsSubmitting(true);
    try {
      const res = await fetch("/api/portal/products", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(parsed.data),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
      }
      const created = (await res.json()) as MyProduct;
      addToast({ title: "상품이 등록되었습니다.", variant: "default" });
      router.push(`/portal/products/${created.id}`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
      addToast({ title: "등록에 실패했습니다.", description: message, variant: "destructive" });
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="p-6">
      <h1 className="mb-6 text-2xl font-bold">상품 등록</h1>
      <form
        onSubmit={(e) => {
          void handleSubmit(e);
        }}
        className="max-w-lg space-y-4"
        noValidate
      >
        <div className="space-y-1">
          <label htmlFor="product-name" className="text-sm font-medium">
            상품명 <span aria-hidden="true">*</span>
          </label>
          <Input
            id="product-name"
            name="name"
            type="text"
            placeholder="상품명을 입력해 주세요."
            aria-required="true"
            aria-invalid={errors.name !== undefined}
            aria-describedby={errors.name !== undefined ? "product-name-error" : undefined}
          />
          {errors.name && (
            <p id="product-name-error" role="alert" className="text-xs text-destructive">
              {errors.name}
            </p>
          )}
        </div>

        <div className="space-y-1">
          <label htmlFor="product-description" className="text-sm font-medium">
            상품 설명 <span aria-hidden="true">*</span>
          </label>
          <Input
            id="product-description"
            name="description"
            type="text"
            placeholder="상품 설명을 입력해 주세요."
            aria-required="true"
            aria-invalid={errors.description !== undefined}
            aria-describedby={
              errors.description !== undefined ? "product-description-error" : undefined
            }
          />
          {errors.description && (
            <p id="product-description-error" role="alert" className="text-xs text-destructive">
              {errors.description}
            </p>
          )}
        </div>

        <div className="space-y-1">
          <label htmlFor="product-price" className="text-sm font-medium">
            가격 (원) <span aria-hidden="true">*</span>
          </label>
          <Input
            id="product-price"
            name="price"
            type="number"
            min={1}
            step={1}
            placeholder="가격을 입력해 주세요."
            aria-required="true"
            aria-invalid={errors.price !== undefined}
            aria-describedby={errors.price !== undefined ? "product-price-error" : undefined}
          />
          {errors.price && (
            <p id="product-price-error" role="alert" className="text-xs text-destructive">
              {errors.price}
            </p>
          )}
        </div>

        <div className="flex gap-2 pt-2">
          <Button type="submit" disabled={isSubmitting} aria-label="상품 등록 제출">
            {isSubmitting ? "등록 중..." : "등록"}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              router.back();
            }}
            aria-label="등록 취소"
          >
            취소
          </Button>
        </div>
      </form>
    </main>
  );
}
