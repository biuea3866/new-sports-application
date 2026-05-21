import Link from "next/link";
import { listMyProducts } from "@/lib/portal/products";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { MyProduct } from "@/lib/portal/types";

interface PageProps {
  searchParams: { page?: string };
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "활성",
  INACTIVE: "비활성",
};

const STATUS_BADGE_VARIANT: Record<string, "default" | "secondary" | "outline"> = {
  ACTIVE: "default",
  INACTIVE: "outline",
};

function ProductRow({ product }: { product: MyProduct }) {
  return (
    <tr className="border-b last:border-0 hover:bg-muted/30 transition-colors">
      <td className="py-3 px-4 text-sm font-medium">
        <Link
          href={`/portal/products/${product.id}`}
          className="hover:underline text-primary"
          aria-label={`${product.name} 상세 보기`}
        >
          {product.name}
        </Link>
      </td>
      <td className="py-3 px-4 text-sm">{product.price.toLocaleString("ko-KR")}원</td>
      <td className="py-3 px-4">
        <Badge variant={STATUS_BADGE_VARIANT[product.status] ?? "secondary"} className="text-xs">
          {STATUS_LABELS[product.status] ?? product.status}
        </Badge>
      </td>
      <td className="py-3 px-4 text-sm">{product.stockQuantity.toLocaleString("ko-KR")}개</td>
      <td className="py-3 px-4">
        <Link href={`/portal/products/${product.id}`} aria-label={`${product.name} 관리`}>
          <Button variant="ghost" size="sm">
            관리
          </Button>
        </Link>
      </td>
    </tr>
  );
}

export default async function ProductsPage({ searchParams }: PageProps) {
  const page = Math.max(0, parseInt(searchParams.page ?? "0", 10) || 0);
  const size = 10;

  let result;
  let errorMessage: string | null = null;

  try {
    result = await listMyProducts({ page, size });
  } catch {
    errorMessage = "상품 목록을 불러오는 중 오류가 발생했습니다.";
  }

  return (
    <main className="max-w-5xl mx-auto px-4 py-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">내 상품 관리</h1>
        <Link href="/portal/products/new">
          <Button aria-label="새 상품 등록">새 상품 등록</Button>
        </Link>
      </div>

      {errorMessage ? (
        <div role="alert" className="rounded-md border border-destructive p-4 text-sm text-destructive">
          {errorMessage}
        </div>
      ) : !result || result.content.length === 0 ? (
        <div className="rounded-md border p-8 text-center text-sm text-muted-foreground">
          등록된 상품이 없습니다.{" "}
          <Link href="/portal/products/new" className="text-primary hover:underline">
            새 상품을 등록해 보세요.
          </Link>
        </div>
      ) : (
        <>
          <div className="rounded-md border overflow-x-auto">
            <table className="w-full text-left" aria-label="내 상품 목록">
              <thead className="bg-muted/50">
                <tr>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    상품명
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    가격
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    상태
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    재고
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    <span className="sr-only">액션</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((product) => (
                  <ProductRow key={product.id} product={product} />
                ))}
              </tbody>
            </table>
          </div>

          {result.totalPages > 1 && (
            <nav aria-label="페이지 네비게이션" className="flex items-center justify-center gap-2">
              {page > 0 && (
                <Link href={`/portal/products?page=${page - 1}`}>
                  <Button variant="outline" size="sm" aria-label="이전 페이지">
                    이전
                  </Button>
                </Link>
              )}
              <span className="text-sm text-muted-foreground" aria-live="polite">
                {page + 1} / {result.totalPages}
              </span>
              {page + 1 < result.totalPages && (
                <Link href={`/portal/products?page=${page + 1}`}>
                  <Button variant="outline" size="sm" aria-label="다음 페이지">
                    다음
                  </Button>
                </Link>
              )}
            </nav>
          )}
        </>
      )}
    </main>
  );
}
