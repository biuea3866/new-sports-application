/**
 * Product B2B API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 */
import "server-only";
import { beClient } from "@/lib/server/be-client";
import { MyProductSchema, MyProductPageSchema } from "./schemas";
import { throwIfErrorResponse } from "./error";
import type {
  MyProduct,
  Page,
  CreateProductInput,
  UpdateProductInput,
  RestoreStockInput,
} from "./types";

export async function getMyProduct(id: number): Promise<MyProduct> {
  const response = await beClient(`/api/goods-seller/products/${id}`, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductSchema.parse(data);
}

export async function createMyProduct(input: CreateProductInput): Promise<MyProduct> {
  const response = await beClient("/api/goods-seller/products", {
    method: "POST",
    body: JSON.stringify(input),
  });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductSchema.parse(data);
}

export async function listMyProducts(params: {
  page?: number;
  size?: number;
} = {}): Promise<Page<MyProduct>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));

  const query = searchParams.toString();
  const path = query ? `/api/goods-seller/products?${query}` : "/api/goods-seller/products";

  const response = await beClient(path, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductPageSchema.parse(data);
}

export async function updateMyProduct(
  id: number,
  patch: UpdateProductInput
): Promise<MyProduct> {
  const response = await beClient(`/api/goods-seller/products/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductSchema.parse(data);
}

export async function activateMyProduct(id: number): Promise<MyProduct> {
  const response = await beClient(`/api/goods-seller/products/${id}/activate`, { method: "POST" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductSchema.parse(data);
}

export async function deactivateMyProduct(id: number): Promise<MyProduct> {
  const response = await beClient(`/api/goods-seller/products/${id}/deactivate`, { method: "POST" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductSchema.parse(data);
}

export async function restoreProductStock(
  id: number,
  input: RestoreStockInput
): Promise<MyProduct> {
  const response = await beClient(`/api/goods-seller/products/${id}/stock/restore`, {
    method: "POST",
    body: JSON.stringify(input),
  });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyProductSchema.parse(data);
}
