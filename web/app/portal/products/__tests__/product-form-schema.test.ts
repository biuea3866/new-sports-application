/**
 * 상품 폼 스키마 단위 테스트
 * [U-01] productFormSchema — quantity > 0 검증
 * [U-02] productFormSchema — price > 0 검증
 * [U-03] restoreStockFormSchema — quantity > 0 (양수) 검증
 * [U-04] restoreStockFormSchema — quantity <= 0 거부
 */
import { describe, it, expect } from "vitest";
import {
  productFormSchema,
  restoreStockFormSchema,
} from "../product-form-schema";

describe("[U-01/U-02] productFormSchema", () => {
  it("[U-01] name, description, price가 유효하면 parse에 성공한다", () => {
    const result = productFormSchema.safeParse({
      name: "스포츠 양말",
      description: "고품질 스포츠 양말",
      price: 9900,
    });
    expect(result.success).toBe(true);
  });

  it("[U-02] price = 0이면 검증에 실패한다 (positive 조건)", () => {
    const result = productFormSchema.safeParse({
      name: "스포츠 양말",
      description: "고품질 스포츠 양말",
      price: 0,
    });
    expect(result.success).toBe(false);
  });

  it("price < 0이면 검증에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "스포츠 양말",
      description: "고품질 스포츠 양말",
      price: -100,
    });
    expect(result.success).toBe(false);
  });

  it("name이 빈 문자열이면 검증에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "",
      description: "설명",
      price: 1000,
    });
    expect(result.success).toBe(false);
  });

  it("description이 빈 문자열이면 검증에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "상품명",
      description: "",
      price: 1000,
    });
    expect(result.success).toBe(false);
  });

  it("price가 undefined이면 검증에 실패한다", () => {
    const result = productFormSchema.safeParse({
      name: "상품명",
      description: "설명",
      price: undefined,
    });
    expect(result.success).toBe(false);
  });
});

describe("[U-03/U-04] restoreStockFormSchema", () => {
  it("[U-03] quantity = 1이면 검증에 성공한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 1 });
    expect(result.success).toBe(true);
  });

  it("[U-03] quantity = 100이면 검증에 성공한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 100 });
    expect(result.success).toBe(true);
  });

  it("[U-04] quantity = 0이면 검증에 실패한다 (positive 조건)", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 0 });
    expect(result.success).toBe(false);
  });

  it("[U-04] quantity = -1이면 검증에 실패한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: -1 });
    expect(result.success).toBe(false);
  });

  it("[U-04] quantity가 undefined이면 검증에 실패한다", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: undefined });
    expect(result.success).toBe(false);
  });

  it("[U-04] quantity가 소수이면 검증에 실패한다 (int 조건)", () => {
    const result = restoreStockFormSchema.safeParse({ quantity: 1.5 });
    expect(result.success).toBe(false);
  });
});
