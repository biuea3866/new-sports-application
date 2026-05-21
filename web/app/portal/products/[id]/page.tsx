"use client";

import { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/components/ui/toast";
import { productUpdateFormSchema, restoreStockFormSchema } from "../product-form-schema";
import type { MyProduct } from "@/lib/portal/types";

// ─── RestoreStockDialog ───────────────────────────────────────────────────────

interface RestoreStockDialogProps {
  onConfirm: (quantity: number) => Promise<void>;
  onClose: () => void;
  isSubmitting: boolean;
}

function RestoreStockDialog({ onConfirm, onClose, isSubmitting }: RestoreStockDialogProps) {
  const [quantityError, setQuantityError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const raw = Number((new FormData(form).get("quantity") as string | null) ?? "");
    const parsed = restoreStockFormSchema.safeParse({ quantity: raw });
    if (!parsed.success) {
      setQuantityError(parsed.error.issues[0]?.message ?? "올바른 수량을 입력해 주세요.");
      return;
    }
    setQuantityError(null);
    await onConfirm(parsed.data.quantity);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="재고 보충"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
    >
      <div className="w-full max-w-sm rounded-lg bg-background p-6 shadow-lg">
        <h2 className="mb-4 text-lg font-semibold">재고 보충</h2>
        <form
          onSubmit={(e) => {
            void handleSubmit(e);
          }}
          className="space-y-4"
          noValidate
        >
          <div className="space-y-1">
            <label htmlFor="restore-quantity" className="text-sm font-medium">
              보충 수량 <span aria-hidden="true">*</span>
            </label>
            <Input
              id="restore-quantity"
              name="quantity"
              type="number"
              min={1}
              step={1}
              placeholder="보충할 수량을 입력해 주세요."
              aria-required="true"
              aria-invalid={quantityError !== null}
              aria-describedby={quantityError !== null ? "restore-quantity-error" : undefined}
            />
            {quantityError && (
              <p id="restore-quantity-error" role="alert" className="text-xs text-destructive">
                {quantityError}
              </p>
            )}
          </div>
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              aria-label="재고 보충 취소"
            >
              취소
            </Button>
            <Button type="submit" disabled={isSubmitting} aria-label="재고 보충 확인">
              {isSubmitting ? "처리 중..." : "보충"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ─── EditForm ─────────────────────────────────────────────────────────────────

interface EditFormProps {
  product: MyProduct;
  onSaved: (updated: MyProduct) => void;
}

function EditForm({ product, onSaved }: EditFormProps) {
  const { addToast } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<
    Partial<Record<"name" | "description" | "price", string>>
  >({});

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);

    const nameVal = (data.get("name") as string | null) ?? "";
    const descVal = (data.get("description") as string | null) ?? "";
    const priceStr = (data.get("price") as string | null) ?? "";

    const raw: Record<string, unknown> = {};
    if (nameVal !== "") raw["name"] = nameVal;
    if (descVal !== "") raw["description"] = descVal;
    if (priceStr !== "") raw["price"] = Number(priceStr);

    const parsed = productUpdateFormSchema.safeParse(raw);
    if (!parsed.success) {
      const fieldErrors: Partial<Record<"name" | "description" | "price", string>> = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as "name" | "description" | "price" | undefined;
        if (field) fieldErrors[field] = issue.message;
      }
      if (Object.keys(fieldErrors).length === 0) {
        addToast({
          title: "수정할 항목을 입력해 주세요.",
          variant: "destructive",
        });
      }
      setErrors(fieldErrors);
      return;
    }

    setErrors({});
    setIsSubmitting(true);
    try {
      const res = await fetch(`/api/portal/products/${product.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(parsed.data),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
      }
      const updated = (await res.json()) as MyProduct;
      onSaved(updated);
      addToast({ title: "상품 정보가 수정되었습니다.", variant: "default" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
      addToast({ title: "수정에 실패했습니다.", description: message, variant: "destructive" });
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={(e) => {
        void handleSubmit(e);
      }}
      className="space-y-4"
      noValidate
    >
      <div className="space-y-1">
        <label htmlFor="edit-name" className="text-sm font-medium">
          상품명
        </label>
        <Input
          id="edit-name"
          name="name"
          type="text"
          defaultValue={product.name}
          aria-invalid={errors.name !== undefined}
          aria-describedby={errors.name !== undefined ? "edit-name-error" : undefined}
        />
        {errors.name && (
          <p id="edit-name-error" role="alert" className="text-xs text-destructive">
            {errors.name}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor="edit-description" className="text-sm font-medium">
          상품 설명
        </label>
        <Input
          id="edit-description"
          name="description"
          type="text"
          defaultValue={product.description}
          aria-invalid={errors.description !== undefined}
          aria-describedby={
            errors.description !== undefined ? "edit-description-error" : undefined
          }
        />
        {errors.description && (
          <p id="edit-description-error" role="alert" className="text-xs text-destructive">
            {errors.description}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor="edit-price" className="text-sm font-medium">
          가격 (원)
        </label>
        <Input
          id="edit-price"
          name="price"
          type="number"
          min={1}
          step={1}
          defaultValue={product.price}
          aria-invalid={errors.price !== undefined}
          aria-describedby={errors.price !== undefined ? "edit-price-error" : undefined}
        />
        {errors.price && (
          <p id="edit-price-error" role="alert" className="text-xs text-destructive">
            {errors.price}
          </p>
        )}
      </div>

      <Button type="submit" disabled={isSubmitting} aria-label="상품 정보 저장">
        {isSubmitting ? "저장 중..." : "저장"}
      </Button>
    </form>
  );
}

// ─── ProductDetailPage ────────────────────────────────────────────────────────

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { addToast } = useToast();

  const [product, setProduct] = useState<MyProduct | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isActioning, setIsActioning] = useState(false);
  const [showRestoreDialog, setShowRestoreDialog] = useState(false);
  const [isRestoring, setIsRestoring] = useState(false);

  const productId = params.id;

  const loadProduct = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const res = await fetch(`/api/portal/products/${productId}`);
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
      }
      const data = (await res.json()) as MyProduct;
      setProduct(data);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "상품 정보를 불러올 수 없습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [productId]);

  useEffect(() => {
    void loadProduct();
  }, [loadProduct]);

  async function handleActivate() {
    setIsActioning(true);
    try {
      const res = await fetch(`/api/portal/products/${productId}/activate`, { method: "POST" });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
      }
      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      addToast({ title: "상품이 활성화되었습니다.", variant: "default" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
      addToast({ title: "활성화에 실패했습니다.", description: message, variant: "destructive" });
    } finally {
      setIsActioning(false);
    }
  }

  async function handleDeactivate() {
    setIsActioning(true);
    try {
      const res = await fetch(`/api/portal/products/${productId}/deactivate`, { method: "POST" });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
      }
      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      addToast({ title: "상품이 비활성화되었습니다.", variant: "default" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
      addToast({
        title: "비활성화에 실패했습니다.",
        description: message,
        variant: "destructive",
      });
    } finally {
      setIsActioning(false);
    }
  }

  async function handleRestoreStock(quantity: number) {
    setIsRestoring(true);
    try {
      const res = await fetch(`/api/portal/products/${productId}/stock/restore`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ quantity }),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
      }
      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      setShowRestoreDialog(false);
      addToast({ title: "재고가 보충되었습니다.", variant: "default" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
      addToast({
        title: "재고 보충에 실패했습니다.",
        description: message,
        variant: "destructive",
      });
    } finally {
      setIsRestoring(false);
    }
  }

  if (isLoading) {
    return (
      <main className="p-6">
        <p aria-live="polite">상품 정보를 불러오는 중...</p>
      </main>
    );
  }

  if (loadError ?? !product) {
    return (
      <main className="p-6">
        <p role="alert" className="text-destructive">
          {loadError ?? "상품 정보를 불러올 수 없습니다."}
        </p>
        <Button
          variant="outline"
          onClick={() => {
            router.back();
          }}
          className="mt-4"
          aria-label="이전 페이지로 돌아가기"
        >
          돌아가기
        </Button>
      </main>
    );
  }

  return (
    <>
      <main className="p-6">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-bold">{product.name}</h1>
          <Button
            variant="outline"
            onClick={() => {
              router.push("/portal/products");
            }}
            aria-label="상품 목록으로 이동"
          >
            목록
          </Button>
        </div>

        <section aria-label="상품 기본 정보" className="mb-8 space-y-3">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">상태</span>
            <Badge variant={product.status === "ACTIVE" ? "default" : "outline"}>
              {product.status === "ACTIVE" ? "활성" : "비활성"}
            </Badge>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">가격</span>
            <span>{product.price.toLocaleString("ko-KR")}원</span>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">재고</span>
            <span>{product.stockQuantity.toLocaleString("ko-KR")}</span>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">설명</span>
            <span>{product.description}</span>
          </div>
        </section>

        <section aria-label="상품 관리 액션" className="mb-8 flex flex-wrap gap-2">
          {product.status === "INACTIVE" ? (
            <Button
              onClick={() => {
                void handleActivate();
              }}
              disabled={isActioning}
              aria-label="상품 활성화"
            >
              {isActioning ? "처리 중..." : "활성화"}
            </Button>
          ) : (
            <Button
              variant="outline"
              onClick={() => {
                void handleDeactivate();
              }}
              disabled={isActioning}
              aria-label="상품 비활성화"
            >
              {isActioning ? "처리 중..." : "비활성화"}
            </Button>
          )}
          <Button
            variant="secondary"
            onClick={() => {
              setShowRestoreDialog(true);
            }}
            aria-label="재고 보충 다이얼로그 열기"
          >
            재고 보충
          </Button>
        </section>

        <section aria-label="상품 정보 수정" className="max-w-lg">
          <h2 className="mb-4 text-lg font-semibold">정보 수정</h2>
          <EditForm
            product={product}
            onSaved={(updated) => {
              setProduct(updated);
            }}
          />
        </section>
      </main>

      {showRestoreDialog && (
        <RestoreStockDialog
          onConfirm={handleRestoreStock}
          onClose={() => {
            setShowRestoreDialog(false);
          }}
          isSubmitting={isRestoring}
        />
      )}
    </>
  );
}
