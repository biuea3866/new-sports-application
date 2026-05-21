// @vitest-environment jsdom
/**
 * 상품 페이지 시나리오 테스트
 * [S-01] 등록 → 활성화 → 재고 보충 → 비활성화 골든 패스 흐름
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import * as React from "react";

const mockPush = vi.fn();
const mockParams: { id: string } = { id: "1" };

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => mockParams,
}));

vi.mock("@/components/ui/toast", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/ui/toast")>();
  return {
    ...original,
    useToast: () => ({ addToast: vi.fn(), toasts: [], removeToast: vi.fn() }),
    ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

const mockFetch = vi.fn();

const baseProduct = {
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

describe("[S-01] 상품 등록 → 활성화 → 재고 보충 → 비활성화 골든 패스", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
    mockPush.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("등록 폼 제출 시 POST /api/portal/products를 호출하고 상세 페이지로 이동한다", async () => {
    const { default: NewProductPage } = await import("../new/page");

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ ...baseProduct, id: 1 }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<NewProductPage />);

    fireEvent.change(screen.getByLabelText(/상품명/i), {
      target: { value: "스포츠 양말" },
    });
    fireEvent.change(screen.getByLabelText(/상품 설명/i), {
      target: { value: "고품질 스포츠 양말" },
    });
    fireEvent.change(screen.getByLabelText(/가격/i), {
      target: { value: "9900" },
    });

    fireEvent.submit(screen.getByRole("form", { name: /상품 등록 폼/i }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products",
        expect.objectContaining({ method: "POST" })
      );
      expect(mockPush).toHaveBeenCalledWith("/portal/products/1");
    });
  });

  it("상세 페이지에서 활성화 버튼 클릭 시 POST /api/portal/products/1/activate를 호출한다", async () => {
    const { default: ProductDetailPage } = await import("../[id]/page");

    const activatedProduct = { ...baseProduct, status: "ACTIVE" };

    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(baseProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(activatedProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("스포츠 양말")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /상품 활성화/i }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products/1/activate",
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  it("상세 페이지에서 재고 보충 버튼 클릭 후 수량 입력, 보충 확인 시 POST /api/portal/products/1/stock/restore를 호출한다", async () => {
    const { default: ProductDetailPage } = await import("../[id]/page");

    const activeProduct = { ...baseProduct, status: "ACTIVE", stockQuantity: 0 };
    const restoredProduct = { ...activeProduct, stockQuantity: 10 };

    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(activeProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(restoredProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("스포츠 양말")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /재고 보충/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/수량/i), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: /재고 보충 확인/i }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products/1/stock/restore",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ quantity: 10 }),
        })
      );
    });
  });

  it("상세 페이지에서 비활성화 버튼 클릭 시 POST /api/portal/products/1/deactivate를 호출한다", async () => {
    const { default: ProductDetailPage } = await import("../[id]/page");

    const activeProduct = { ...baseProduct, status: "ACTIVE", stockQuantity: 10 };
    const deactivatedProduct = { ...activeProduct, status: "INACTIVE" };

    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(activeProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(deactivatedProduct), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("스포츠 양말")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /상품 비활성화/i }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/products/1/deactivate",
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  it("재고 음수 입력 시 클라이언트 검증이 실패하고 API를 호출하지 않는다", async () => {
    const { default: ProductDetailPage } = await import("../[id]/page");

    const activeProduct = { ...baseProduct, status: "ACTIVE" };

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(activeProduct), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<ProductDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("스포츠 양말")).toBeInTheDocument();
    });

    mockFetch.mockReset();

    fireEvent.click(screen.getByRole("button", { name: /재고 보충/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/수량/i), { target: { value: "-5" } });
    fireEvent.click(screen.getByRole("button", { name: /재고 보충 확인/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    expect(mockFetch).not.toHaveBeenCalledWith(
      "/api/portal/products/1/stock/restore",
      expect.anything()
    );
  });
});
