"use client";

import * as React from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { useToast } from "@/components/ui/toast";
import {
  productUpdateFormSchema,
  restoreStockFormSchema,
} from "@/app/portal/products/product-form-schema";
import type { MyProduct } from "@/lib/portal/types";

// ─── 상태 레이블 ──────────────────────────────────────────────────────────────

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "활성",
  INACTIVE: "비활성",
};

const STATUS_BADGE_VARIANT: Record<string, "default" | "outline"> = {
  ACTIVE: "default",
  INACTIVE: "outline",
};

// ─── 재고 보충 Dialog ─────────────────────────────────────────────────────────

interface RestoreStockDialogProps {
  isOpen: boolean;
  isSubmitting: boolean;
  error: string | null;
  onConfirm: (quantity: number) => void;
  onClose: () => void;
}

function RestoreStockDialog({
  isOpen,
  isSubmitting,
  error,
  onConfirm,
  onClose,
}: RestoreStockDialogProps) {
  const [quantityText, setQuantityText] = React.useState("");
  const [quantityError, setQuantityError] = React.useState<string | undefined>(undefined);

  function handleConfirm() {
    const n = parseInt(quantityText, 10);
    const parsed = restoreStockFormSchema.safeParse({ quantity: isNaN(n) ? undefined : n });
    if (!parsed.success) {
      setQuantityError(parsed.error.flatten().fieldErrors["quantity"]?.[0]);
      return;
    }
    setQuantityError(undefined);
    onConfirm(parsed.data.quantity);
  }

  function handleClose() {
    setQuantityText("");
    setQuantityError(undefined);
    onClose();
  }

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="restore-stock-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    >
      <div className="bg-background border rounded-lg p-6 max-w-sm w-full mx-4 space-y-4">
        <h2 id="restore-stock-dialog-title" className="text-lg font-semibold">
          재고 보충
        </h2>
        <p className="text-sm text-muted-foreground">보충할 재고 수량을 입력해 주세요.</p>

        {error !== null && (
          <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="restore-quantity" className="block text-sm font-medium mb-1">
            수량 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="restore-quantity"
            type="number"
            min={1}
            value={quantityText}
            onChange={(e) => {
              setQuantityText(e.target.value);
              setQuantityError(undefined);
            }}
            placeholder="10"
            aria-required="true"
            aria-describedby={quantityError !== undefined ? "restore-quantity-error" : undefined}
            aria-invalid={quantityError !== undefined}
          />
          {quantityError !== undefined && (
            <p id="restore-quantity-error" className="text-xs text-destructive mt-1" role="alert">
              {quantityError}
            </p>
          )}
        </div>

        <div className="flex gap-2 justify-end">
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting} aria-label="취소">
            취소
          </Button>
          <Button onClick={handleConfirm} disabled={isSubmitting} aria-label="재고 보충 확인">
            {isSubmitting ? "처리 중..." : "보충"}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── 수정 폼 ──────────────────────────────────────────────────────────────────

interface EditFormProps {
  product: MyProduct;
  isSubmitting: boolean;
  error: string | null;
  onSubmit: (patch: { name?: string; description?: string; price?: number }) => void;
  onCancel: () => void;
}

interface EditFieldErrors {
  name?: string;
  description?: string;
  price?: string;
}

function EditForm({ product, isSubmitting, error, onSubmit, onCancel }: EditFormProps) {
  const [name, setName] = React.useState(product.name);
  const [description, setDescription] = React.useState(product.description);
  const [priceText, setPriceText] = React.useState(String(product.price));
  const [fieldErrors, setFieldErrors] = React.useState<EditFieldErrors>({});

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    const priceNum = parseInt(priceText, 10);
    const patch = {
      name: name !== product.name ? name : undefined,
      description: description !== product.description ? description : undefined,
      price: !isNaN(priceNum) && priceNum !== product.price ? priceNum : undefined,
    };

    const parsed = productUpdateFormSchema.safeParse(patch);
    if (!parsed.success) {
      const fe = parsed.error.flatten().fieldErrors;
      setFieldErrors({
        name: fe["name"]?.[0],
        description: fe["description"]?.[0],
        price: fe["price"]?.[0],
      });
      return;
    }

    setFieldErrors({});
    onSubmit(parsed.data);
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-label="상품 수정 폼"
      className="space-y-4"
    >
      {error !== null && (
        <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <div>
        <label htmlFor="edit-product-name" className="block text-sm font-medium mb-1">
          상품명
        </label>
        <Input
          id="edit-product-name"
          type="text"
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setFieldErrors((prev) => ({ ...prev, name: undefined }));
          }}
          aria-describedby={fieldErrors.name !== undefined ? "edit-name-error" : undefined}
          aria-invalid={fieldErrors.name !== undefined}
        />
        {fieldErrors.name !== undefined && (
          <p id="edit-name-error" className="text-xs text-destructive mt-1" role="alert">
            {fieldErrors.name}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="edit-product-description" className="block text-sm font-medium mb-1">
          상품 설명
        </label>
        <textarea
          id="edit-product-description"
          value={description}
          onChange={(e) => {
            setDescription(e.target.value);
            setFieldErrors((prev) => ({ ...prev, description: undefined }));
          }}
          rows={4}
          className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none"
          aria-describedby={fieldErrors.description !== undefined ? "edit-description-error" : undefined}
          aria-invalid={fieldErrors.description !== undefined}
        />
        {fieldErrors.description !== undefined && (
          <p id="edit-description-error" className="text-xs text-destructive mt-1" role="alert">
            {fieldErrors.description}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="edit-product-price" className="block text-sm font-medium mb-1">
          가격 (원)
        </label>
        <Input
          id="edit-product-price"
          type="number"
          min={1}
          value={priceText}
          onChange={(e) => {
            setPriceText(e.target.value);
            setFieldErrors((prev) => ({ ...prev, price: undefined }));
          }}
          aria-describedby={fieldErrors.price !== undefined ? "edit-price-error" : undefined}
          aria-invalid={fieldErrors.price !== undefined}
        />
        {fieldErrors.price !== undefined && (
          <p id="edit-price-error" className="text-xs text-destructive mt-1" role="alert">
            {fieldErrors.price}
          </p>
        )}
      </div>

      <div className="flex gap-2 pt-2">
        <Button type="submit" disabled={isSubmitting} aria-disabled={isSubmitting}>
          {isSubmitting ? "저장 중..." : "저장"}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel} aria-label="수정 취소">
          취소
        </Button>
      </div>
    </form>
  );
}

// ─── 상세 페이지 ──────────────────────────────────────────────────────────────

type ViewMode = "detail" | "edit";

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { addToast } = useToast();

  const [product, setProduct] = React.useState<MyProduct | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [loadError, setLoadError] = React.useState<string | null>(null);

  const [viewMode, setViewMode] = React.useState<ViewMode>("detail");
  const [isUpdating, setIsUpdating] = React.useState(false);
  const [updateError, setUpdateError] = React.useState<string | null>(null);

  const [isActing, setIsActing] = React.useState(false);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const [showRestoreStock, setShowRestoreStock] = React.useState(false);
  const [isRestoringStock, setIsRestoringStock] = React.useState(false);
  const [restoreStockError, setRestoreStockError] = React.useState<string | null>(null);

  React.useEffect(() => {
    async function fetchProduct() {
      try {
        const res = await fetch(`/api/portal/products/${id}`);
        if (!res.ok) {
          const body = (await res.json()) as { message?: string };
          setLoadError(body.message ?? "상품 정보를 불러오지 못했습니다.");
          return;
        }
        const data = (await res.json()) as MyProduct;
        setProduct(data);
      } catch {
        setLoadError("네트워크 오류가 발생했습니다.");
      } finally {
        setLoading(false);
      }
    }
    void fetchProduct();
  }, [id]);

  async function handleUpdate(patch: { name?: string; description?: string; price?: number }) {
    setIsUpdating(true);
    setUpdateError(null);

    try {
      const res = await fetch(`/api/portal/products/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(patch),
      });

      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setUpdateError(body.message ?? "수정 중 오류가 발생했습니다.");
        return;
      }

      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      setViewMode("detail");
      addToast({ title: "상품 정보가 수정됐습니다.", variant: "default" });
    } catch {
      setUpdateError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsUpdating(false);
    }
  }

  async function handleActivate() {
    setIsActing(true);
    setActionError(null);

    try {
      const res = await fetch(`/api/portal/products/${id}/activate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      });

      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setActionError(body.message ?? "활성화 중 오류가 발생했습니다.");
        return;
      }

      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      addToast({ title: "상품이 활성화됐습니다.", variant: "default" });
    } catch {
      setActionError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsActing(false);
    }
  }

  async function handleDeactivate() {
    setIsActing(true);
    setActionError(null);

    try {
      const res = await fetch(`/api/portal/products/${id}/deactivate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      });

      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setActionError(body.message ?? "비활성화 중 오류가 발생했습니다.");
        return;
      }

      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      addToast({ title: "상품이 비활성화됐습니다.", variant: "default" });
    } catch {
      setActionError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsActing(false);
    }
  }

  async function handleRestoreStock(quantity: number) {
    setIsRestoringStock(true);
    setRestoreStockError(null);

    try {
      const res = await fetch(`/api/portal/products/${id}/stock/restore`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ quantity }),
      });

      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setRestoreStockError(body.message ?? "재고 보충 중 오류가 발생했습니다.");
        return;
      }

      const updated = (await res.json()) as MyProduct;
      setProduct(updated);
      setShowRestoreStock(false);
      addToast({ title: `재고가 ${quantity}개 보충됐습니다.`, variant: "default" });
    } catch {
      setRestoreStockError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsRestoringStock(false);
    }
  }

  if (loading) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-8">
        <p className="text-sm text-muted-foreground" aria-live="polite" aria-busy="true">
          불러오는 중...
        </p>
      </main>
    );
  }

  if (loadError ?? !product) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-8 space-y-4">
        <div role="alert" className="rounded-md border border-destructive p-4 text-sm text-destructive">
          {loadError ?? "상품 정보를 찾을 수 없습니다."}
        </div>
        <Button variant="outline" onClick={() => router.push("/portal/products")} aria-label="목록으로 돌아가기">
          목록으로
        </Button>
      </main>
    );
  }

  const canActivate = product.status === "INACTIVE";
  const canDeactivate = product.status === "ACTIVE";

  return (
    <>
      <RestoreStockDialog
        isOpen={showRestoreStock}
        isSubmitting={isRestoringStock}
        error={restoreStockError}
        onConfirm={(qty) => { void handleRestoreStock(qty); }}
        onClose={() => {
          setShowRestoreStock(false);
          setRestoreStockError(null);
        }}
      />

      <main className="max-w-2xl mx-auto px-4 py-8 space-y-6">
        <div>
          <a
            href="/portal/products"
            className="text-sm text-muted-foreground hover:text-foreground transition-colors"
            aria-label="내 상품 목록으로 돌아가기"
          >
            ← 내 상품 목록
          </a>
        </div>

        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <h1 className="text-2xl font-bold tracking-tight">{product.name}</h1>
            <Badge variant={STATUS_BADGE_VARIANT[product.status] ?? "secondary"}>
              {STATUS_LABELS[product.status] ?? product.status}
            </Badge>
          </div>

          {viewMode === "detail" && (
            <div className="flex gap-2 flex-wrap justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setViewMode("edit")}
                aria-label={`${product.name} 수정`}
              >
                수정
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowRestoreStock(true)}
                aria-label="재고 보충"
              >
                재고 보충
              </Button>
              {canActivate && (
                <Button
                  size="sm"
                  onClick={() => { void handleActivate(); }}
                  disabled={isActing}
                  aria-disabled={isActing}
                  aria-label="상품 활성화"
                >
                  {isActing ? "처리 중..." : "활성화"}
                </Button>
              )}
              {canDeactivate && (
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => { void handleDeactivate(); }}
                  disabled={isActing}
                  aria-disabled={isActing}
                  aria-label="상품 비활성화"
                >
                  {isActing ? "처리 중..." : "비활성화"}
                </Button>
              )}
            </div>
          )}
        </div>

        {actionError !== null && (
          <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
            {actionError}
          </div>
        )}

        {viewMode === "detail" ? (
          <section aria-label="상품 상세 정보" className="rounded-md border p-6 space-y-4">
            <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
              <div>
                <dt className="font-medium text-muted-foreground">가격</dt>
                <dd className="mt-1">{product.price.toLocaleString("ko-KR")}원</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">재고 수량</dt>
                <dd className="mt-1">{product.stockQuantity.toLocaleString("ko-KR")}개</dd>
              </div>
              <div className="col-span-2">
                <dt className="font-medium text-muted-foreground">설명</dt>
                <dd className="mt-1 whitespace-pre-wrap">{product.description}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">등록일</dt>
                <dd className="mt-1">{new Date(product.createdAt).toLocaleDateString("ko-KR")}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">최종 수정일</dt>
                <dd className="mt-1">{new Date(product.updatedAt).toLocaleDateString("ko-KR")}</dd>
              </div>
            </dl>
          </section>
        ) : (
          <section aria-label="상품 수정">
            <EditForm
              product={product}
              isSubmitting={isUpdating}
              error={updateError}
              onSubmit={(patch) => { void handleUpdate(patch); }}
              onCancel={() => {
                setViewMode("detail");
                setUpdateError(null);
              }}
            />
          </section>
        )}
      </main>
    </>
  );
}
