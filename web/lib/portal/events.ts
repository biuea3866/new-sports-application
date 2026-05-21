/**
 * Event B2B API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 */
import "server-only";
import { beClient } from "@/lib/server/be-client";
import { MyEventSchema, MyEventDetailSchema, MyEventPageSchema } from "./schemas";
import { throwIfErrorResponse } from "./error";
import type { MyEvent, MyEventDetail, Page, CreateEventInput } from "./types";

export async function createMyEvent(input: CreateEventInput): Promise<MyEvent> {
  const response = await beClient("/api/b2b/events", {
    method: "POST",
    body: JSON.stringify(input),
  });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyEventSchema.parse(data);
}

export async function listMyEvents(params: {
  page?: number;
  size?: number;
} = {}): Promise<Page<MyEvent>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));

  const query = searchParams.toString();
  const path = query ? `/api/b2b/events?${query}` : "/api/b2b/events";

  const response = await beClient(path, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyEventPageSchema.parse(data);
}

export async function getMyEvent(id: number): Promise<MyEventDetail> {
  const response = await beClient(`/api/b2b/events/${id}`, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyEventDetailSchema.parse(data);
}

export async function openMyEvent(id: number): Promise<MyEvent> {
  const response = await beClient(`/api/b2b/events/${id}/open`, { method: "POST" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyEventSchema.parse(data);
}

export async function closeMyEvent(id: number): Promise<MyEvent> {
  const response = await beClient(`/api/b2b/events/${id}/close`, { method: "POST" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return MyEventSchema.parse(data);
}
