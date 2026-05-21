"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { productFormSchema } from "@/app/portal/products/product-form-schema";
import type { ProductFormValues } from "@/app/portal/products/product-form-schema";

interface FieldErrors {
  name?: string;
  description?: string;
  price?: string;
  _form?: string;
}

function parsePrice(value: string): number | undefined {
  const n = parseInt(value, 10);
  return isNaN(n) ? undefined : n;
}

export default function NewProductPage() {
  const router = useRouter();
  const { addToast } = useToast();

  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [priceText, setPriceText] = React.useState("");
  const [errors, setErrors] = React.useState<FieldErrors>({});
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  function validate(): ProductFormValues | null {
    const parsed = productFormSchema.safeParse({
      name,
      description,
      price: parsePrice(priceText),
    });

    if (parsed.success) {
      setErrors({});
      return parsed.data;
    }

    const fieldErrors = parsed.error.flatten().fieldErrors;
    setErrors({
      name: fieldErrors["name"]?.[0],
      description: fieldErrors["description"]?.[0],
      price: fieldErrors["price"]?.[0],
    });
    return null;
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const values = validate();
    if (!values) return;

    setIsSubmitting(true);
    setErrors({});

    try {
      const res = await fetch("/api/portal/products", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });

      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setErrors({ _form: body.message ?? "등록 중 오류가 발생했습니다." });
        return;
      }

      const created = (await res.json()) as { id: number };
      addToast({ title: "상품이 등록됐습니다.", variant: "default" });
      router.push(`/portal/products/${created.id}`);
    } catch {
      setErrors({ _form: "네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." });
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="max-w-2xl mx-auto px-4 py-8 space-y-6">
      <div>
        <a
          href="/portal/products"
          className="text-sm text-muted-foreground hover:text-foreground transition-colors"
          aria-label="내 상품 목록으로 돌아가기"
        >
          ← 내 상품 목록
        </a>
        <h1 className="text-2xl font-bold mt-2">새 상품 등록</h1>
      </div>

      {errors._form !== undefined && (
        <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
          {errors._form}
        </div>
      )}

      <form onSubmit={(e) => { void handleSubmit(e); }} noValidate aria-label="상품 등록 폼" className="space-y-5">
        <div>
          <label htmlFor="product-name" className="block text-sm font-medium mb-1">
            상품명 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="product-name"
            type="text"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setErrors((prev) => ({ ...prev, name: undefined }));
            }}
            placeholder="스포츠 양말"
            aria-required="true"
            aria-describedby={errors.name !== undefined ? "product-name-error" : undefined}
            aria-invalid={errors.name !== undefined}
          />
          {errors.name !== undefined && (
            <p id="product-name-error" className="text-xs text-destructive mt-1" role="alert">
              {errors.name}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="product-description" className="block text-sm font-medium mb-1">
            상품 설명 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <textarea
            id="product-description"
            value={description}
            onChange={(e) => {
              setDescription(e.target.value);
              setErrors((prev) => ({ ...prev, description: undefined }));
            }}
            placeholder="상품에 대한 설명을 입력해 주세요."
            rows={4}
            className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none"
            aria-required="true"
            aria-describedby={errors.description !== undefined ? "product-description-error" : undefined}
            aria-invalid={errors.description !== undefined}
          />
          {errors.description !== undefined && (
            <p id="product-description-error" className="text-xs text-destructive mt-1" role="alert">
              {errors.description}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="product-price" className="block text-sm font-medium mb-1">
            가격 (원) <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="product-price"
            type="number"
            min={1}
            value={priceText}
            onChange={(e) => {
              setPriceText(e.target.value);
              setErrors((prev) => ({ ...prev, price: undefined }));
            }}
            placeholder="9900"
            aria-required="true"
            aria-describedby={errors.price !== undefined ? "product-price-error" : undefined}
            aria-invalid={errors.price !== undefined}
          />
          {errors.price !== undefined && (
            <p id="product-price-error" className="text-xs text-destructive mt-1" role="alert">
              {errors.price}
            </p>
          )}
        </div>

        <div className="flex gap-3 pt-2">
          <Button
            type="submit"
            disabled={isSubmitting}
            aria-disabled={isSubmitting}
            className="flex-1"
          >
            {isSubmitting ? "등록 중..." : "상품 등록"}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/portal/products")}
            aria-label="등록 취소하고 목록으로 돌아가기"
          >
            취소
          </Button>
        </div>
      </form>
    </main>
  );
}
