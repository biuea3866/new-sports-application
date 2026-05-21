import { describe, it, expect } from "vitest";
import {
  productFormSchema,
  productUpdateFormSchema,
  restoreStockFormSchema,
} from "../product-form-schema";

describe("productFormSchema", () => {
  // [U-01] 정상 입력 통과
  it("[U-01] 유효한 name·description·price는 파싱에 성공한다", () => {
    const result = productFormSchema.safeParse({
      name: "스포츠 음료",
      description: "이온 음료입니다.",
      price: 1500,
    });
    expect(result.success).toBe(true);
  });

  // [U-02] 필수 필드 누락 시 오류
  it("[U-02] name이 빈 문자열이면 유효성 검사에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "",
      description: "설명",
      price: 1000,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "name")).toBe(true);
    }
  });

  it("[U-02] description이 빈 문자열이면 유효성 검사에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "상품명",
      description: "",
      price: 1000,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "description")).toBe(true);
    }
  });

  it("[U-02] price가 0이면 유효성 검사에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "상품명",
      description: "설명",
      price: 0,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "price")).toBe(true);
    }
  });

  it("[U-02] price가 음수이면 유효성 검사에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "상품명",
      description: "설명",
      price: -100,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "price")).toBe(true);
    }
  });
});

describe("productUpdateFormSchema", () => {
  it("[U-01] 하나 이상의 필드를 제공하면 파싱에 성공한다", () => {
    const result = productUpdateFormSchema.safeParse({ name: "새 상품명" });
    expect(result.success).toBe(true);
  });

  it("[U-02] 모든 필드가 undefined이면 유효성 검사에 실패한다", () => {
    const result = productUpdateFormSchema.safeParse({});
    expect(result.success).toBe(false);
  });
});

describe("restoreStockFormSchema", () => {
  // [U-03] 재고 수량 양수 정수
  it("[U-03] quantity가 양수 정수이면 파싱에 성공한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 10 });
    expect(result.success).toBe(true);
  });

  it("[U-04] quantity가 0이면 유효성 검사에 실패한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 0 });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "quantity")).toBe(true);
    }
  });

  it("[U-04] quantity가 음수이면 유효성 검사에 실패한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: -5 });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "quantity")).toBe(true);
    }
  });

  it("[U-04] quantity가 소수이면 유효성 검사에 실패한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 1.5 });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === "quantity")).toBe(true);
    }
  });
});
