import { listMyProducts } from "@/lib/portal/products";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import Link from "next/link";

interface ProductsPageProps {
  searchParams: { page?: string; size?: string };
}

export default async function ProductsPage({ searchParams }: ProductsPageProps) {
  const page = Math.max(0, parseInt(searchParams.page ?? "0", 10) || 0);
  const size = Math.max(1, parseInt(searchParams.size ?? "20", 10) || 20);

  let result;
  try {
    result = await listMyProducts({ page, size });
  } catch {
    return (
      <main className="p-6">
        <h1 className="mb-4 text-2xl font-bold">내 상품 목록</h1>
        <p role="alert" className="text-destructive">
          상품 목록을 불러오는 중 오류가 발생했습니다.
        </p>
      </main>
    );
  }

  const { content, totalPages } = result;

  return (
    <main className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">내 상품 목록</h1>
        <Button asChild>
          <Link href="/portal/products/new" aria-label="새 상품 등록">
            상품 등록
          </Link>
        </Button>
      </div>

      {content.length === 0 ? (
        <p className="text-muted-foreground">등록된 상품이 없습니다.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b bg-muted/50 text-left">
                <th className="px-4 py-3 font-medium">상품명</th>
                <th className="px-4 py-3 font-medium">가격</th>
                <th className="px-4 py-3 font-medium">상태</th>
                <th className="px-4 py-3 font-medium">재고</th>
                <th className="px-4 py-3 font-medium">관리</th>
              </tr>
            </thead>
            <tbody>
              {content.map((product) => (
                <tr key={product.id} className="border-b hover:bg-muted/25">
                  <td className="px-4 py-3">{product.name}</td>
                  <td className="px-4 py-3">{product.price.toLocaleString("ko-KR")}원</td>
                  <td className="px-4 py-3">
                    <Badge variant={product.status === "ACTIVE" ? "default" : "outline"}>
                      {product.status === "ACTIVE" ? "활성" : "비활성"}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">{product.stockQuantity.toLocaleString("ko-KR")}</td>
                  <td className="px-4 py-3">
                    <Button asChild variant="outline" size="sm">
                      <Link
                        href={`/portal/products/${product.id}`}
                        aria-label={`${product.name} 상세 보기`}
                      >
                        상세
                      </Link>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav aria-label="페이지 네비게이션" className="mt-6 flex items-center gap-2">
          {page > 0 && (
            <Button asChild variant="outline" size="sm">
              <Link
                href={`/portal/products?page=${page - 1}&size=${size}`}
                aria-label="이전 페이지"
              >
                이전
              </Link>
            </Button>
          )}
          <span className="text-sm text-muted-foreground">
            {page + 1} / {totalPages}
          </span>
          {page + 1 < totalPages && (
            <Button asChild variant="outline" size="sm">
              <Link
                href={`/portal/products?page=${page + 1}&size=${size}`}
                aria-label="다음 페이지"
              >
                다음
              </Link>
            </Button>
          )}
        </nav>
      )}
    </main>
  );
}
