/**
 * @vitest-environment jsdom
 *
 * Products 화면 시나리오 테스트
 * [S-01] 상품 등록 골든 패스
 * [S-02] 상품 활성화
 * [S-03] 재고 보충
 * [S-04] 상품 비활성화
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import type { MyProduct } from "@/lib/portal/types";

vi.mock("next/navigation", () => ({
  useRouter: vi.fn(),
  useParams: vi.fn(),
}));

vi.mock("@/components/ui/toast", () => ({
  useToast: vi.fn(),
  ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const mockFetch = vi.fn();

const baseProduct: MyProduct = {
  id: 1,
  name: "스포츠 양말",
  description: "고품질 스포츠 양말",
  price: 9900,
  status: "INACTIVE",
  stockQuantity: 0,
  ownerId: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

beforeEach(async () => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", mockFetch);

  const { useRouter, useParams } = await import("next/navigation");
  vi.mocked(useRouter).mockReturnValue({ push: vi.fn(), back: vi.fn() } as ReturnType<
    typeof useRouter
  >);
  vi.mocked(useParams<{ id: string }>).mockReturnValue({ id: "1" });

  const { useToast } = await import("@/components/ui/toast");
  vi.mocked(useToast).mockReturnValue({ addToast: vi.fn(), toasts: [], removeToast: vi.fn() });
});

// [S-01] 상품 등록 골든 패스
describe("[S-01] 상품 등록 페이지", () => {
  it("유효한 입력으로 제출하면 POST /api/portal/products를 호출하고 상세 페이지로 이동한다", async () => {
    const { useRouter } = await import("next/navigation");
    const mockPush = vi.fn();
    vi.mocked(useRouter).mockReturnValue({ push: mockPush, back: vi.fn() } as ReturnType<
      typeof useRouter
    >);

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ ...baseProduct, status: "INACTIVE" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { default: NewProductPage } = await import("../new/page");
    render(<NewProductPage />);

    fireEvent.change(screen.getByLabelText(/상품명/), { target: { value: "스포츠 양말" } });
    fireEvent.change(screen.getByLabelText(/상품 설명/), {
      target: { value: "고품질 스포츠 양말" },
    });
    fireEvent.change(screen.getByLabelText(/가격/), { target: { value: "9900" } });
    fireEvent.click(screen.getByRole("button", { name: "상품 등록 제출" }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(1);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products",
        expect.objectContaining({ method: "POST" })
      );
    });

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/portal/products/1");
    });
  });

  it("필수 필드 미입력 시 에러 메시지를 표시하고 fetch를 호출하지 않는다", async () => {
    const { default: NewProductPage } = await import("../new/page");
    // useToast is already mocked in beforeEach
    render(<NewProductPage />);

    fireEvent.click(screen.getByRole("button", { name: "상품 등록 제출" }));

    await waitFor(() => {
      expect(screen.getByText("상품명을 입력해 주세요.")).toBeTruthy();
    });
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

// [S-02] 상품 활성화
describe("[S-02] 상품 상세 페이지 — 활성화", () => {
  it("INACTIVE 상품의 활성화 버튼 클릭 시 POST .../activate를 호출하고 상태가 ACTIVE로 변경된다", async () => {
    const { useToast } = await import("@/components/ui/toast");
    const mockAddToast = vi.fn();
    vi.mocked(useToast).mockReturnValue({ addToast: mockAddToast, toasts: [], removeToast: vi.fn() }); // override for assertion

    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(baseProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ...baseProduct, status: "ACTIVE" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

    const { default: ProductDetailPage } = await import("../[id]/page");
    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "상품 활성화" })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole("button", { name: "상품 활성화" }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products/1/activate",
        expect.objectContaining({ method: "POST" })
      );
    });

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "상품이 활성화되었습니다." })
      );
    });
  });
});

// [S-03] 재고 보충
describe("[S-03] 상품 상세 페이지 — 재고 보충", () => {
  it("재고 보충 버튼 클릭 후 수량 입력 → 확인 시 POST .../stock/restore를 호출한다", async () => {
    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(baseProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ...baseProduct, stockQuantity: 50 }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

    const { default: ProductDetailPage } = await import("../[id]/page");
    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "재고 보충 다이얼로그 열기" })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole("button", { name: "재고 보충 다이얼로그 열기" }));

    await waitFor(() => {
      expect(screen.getByRole("dialog", { name: "재고 보충" })).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText(/보충 수량/), { target: { value: "50" } });
    fireEvent.click(screen.getByRole("button", { name: "재고 보충 확인" }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products/1/stock/restore",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ quantity: 50 }),
        })
      );
    });
  });
});

// [S-04] 상품 비활성화
describe("[S-04] 상품 상세 페이지 — 비활성화", () => {
  it("ACTIVE 상품의 비활성화 버튼 클릭 시 POST .../deactivate를 호출한다", async () => {
    const { useToast } = await import("@/components/ui/toast");
    const mockAddToast = vi.fn();
    vi.mocked(useToast).mockReturnValue({ addToast: mockAddToast, toasts: [], removeToast: vi.fn() }); // override for assertion

    const activeProduct = { ...baseProduct, status: "ACTIVE" as const };

    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(activeProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ...activeProduct, status: "INACTIVE" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

    const { default: ProductDetailPage } = await import("../[id]/page");
    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "상품 비활성화" })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole("button", { name: "상품 비활성화" }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products/1/deactivate",
        expect.objectContaining({ method: "POST" })
      );
    });

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "상품이 비활성화되었습니다." })
      );
    });
  });
});
