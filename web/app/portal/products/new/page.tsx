"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/components/ui/toast";
import { productFormSchema, PRODUCT_CATEGORIES } from "../product-form-schema";
import type { MyProduct } from "@/lib/portal/types";

const CATEGORY_LABELS: Record<(typeof PRODUCT_CATEGORIES)[number], string> = {
  EQUIPMENT: "장비",
  APPAREL: "의류",
  FOOTWEAR: "신발",
  ACCESSORY: "액세서리",
};

export default function NewProductPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<
    Partial<Record<"name" | "description" | "price" | "category" | "imageUrl", string>>
  >({});

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);

    const raw = {
      name: (data.get("name") as string | null) ?? "",
      description: (data.get("description") as string | null) ?? "",
      price: Number((data.get("price") as string | null) ?? ""),
      category: (data.get("category") as string | null) ?? "",
      imageUrl: (data.get("imageUrl") as string | null) ?? "",
    };

    const parsed = productFormSchema.safeParse(raw);
    if (!parsed.success) {
      const fieldErrors: Partial<
        Record<"name" | "description" | "price" | "category" | "imageUrl", string>
      > = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as
          | "name"
          | "description"
          | "price"
          | "category"
          | "imageUrl"
          | undefined;
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

        <div className="space-y-1">
          <label htmlFor="product-category" className="text-sm font-medium">
            카테고리 <span aria-hidden="true">*</span>
          </label>
          <select
            id="product-category"
            name="category"
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
            aria-required="true"
            aria-invalid={errors.category !== undefined}
            aria-describedby={errors.category !== undefined ? "product-category-error" : undefined}
            defaultValue=""
          >
            <option value="" disabled>
              카테고리를 선택해 주세요.
            </option>
            {PRODUCT_CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {CATEGORY_LABELS[cat]}
              </option>
            ))}
          </select>
          {errors.category && (
            <p id="product-category-error" role="alert" className="text-xs text-destructive">
              {errors.category}
            </p>
          )}
        </div>

        <div className="space-y-1">
          <label htmlFor="product-image-url" className="text-sm font-medium">
            이미지 URL <span aria-hidden="true">*</span>
          </label>
          <Input
            id="product-image-url"
            name="imageUrl"
            type="url"
            placeholder="https://example.com/image.jpg"
            aria-required="true"
            aria-invalid={errors.imageUrl !== undefined}
            aria-describedby={errors.imageUrl !== undefined ? "product-image-url-error" : undefined}
          />
          {errors.imageUrl && (
            <p id="product-image-url-error" role="alert" className="text-xs text-destructive">
              {errors.imageUrl}
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
