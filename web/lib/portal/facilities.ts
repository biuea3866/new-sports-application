/**
 * Facility B2B API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 */
import "server-only";
import { beClient } from "@/lib/server/be-client";
import { MyFacilitySchema, MyFacilityPageSchema } from "./schemas";
import { throwIfErrorResponse } from "./error";
import type {
  MyFacility,
  Page,
  CreateFacilityInput,
  UpdateFacilityInput,
} from "./types";

export async function createMyFacility(input: CreateFacilityInput): Promise<MyFacility> {
  const response = await beClient("/api/b2b/facilities", {
    method: "POST",
    body: JSON.stringify(input),
  });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyFacilitySchema.parse(data);
}

export async function listMyFacilities(params: {
  page?: number;
  size?: number;
} = {}): Promise<Page<MyFacility>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));

  const query = searchParams.toString();
  const path = query ? `/api/b2b/facilities?${query}` : "/api/b2b/facilities";

  const response = await beClient(path, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyFacilityPageSchema.parse(data);
}

export async function getMyFacility(id: string): Promise<MyFacility> {
  const response = await beClient(`/api/b2b/facilities/${id}`, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyFacilitySchema.parse(data);
}

export async function updateMyFacility(
  id: string,
  patch: UpdateFacilityInput
): Promise<MyFacility> {
  const response = await beClient(`/api/b2b/facilities/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyFacilitySchema.parse(data);
}

export async function deleteMyFacility(id: string): Promise<void> {
  const response = await beClient(`/api/b2b/facilities/${id}`, { method: "DELETE" });
  await throwIfErrorResponse(response);
}
