/**
 * home.ts — 홈 탭 API 함수 (이벤트·상품·시설)
 *
 * BE 컨트랙트:
 *   GET /events?page=0&size=5   (public)
 *   GET /products?page=0&size=5 (public)
 *   GET /facilities?page=0&size=5 (public)
 *
 * Mobile은 BFF 없이 BE를 직접 호출합니다 (be-client.ts 참조).
 */
import { getBeClient } from './be-client';

// DTO 타입 정의

export interface EventSummary {
  id: number;
  title: string;
  startAt: string;
  endAt: string;
  location: string;
  thumbnailUrl: string | null;
}

export interface ProductSummary {
  id: number;
  name: string;
  price: number;
  thumbnailUrl: string | null;
}

export interface FacilitySummary {
  id: number;
  name: string;
  address: string;
  thumbnailUrl: string | null;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// API 함수

export async function fetchUpcomingEvents(): Promise<EventSummary[]> {
  const response = await getBeClient().get<PageResponse<EventSummary>>('/events', {
    params: { page: 0, size: 5 },
  });
  return response.data.content;
}

export async function fetchRecommendedProducts(): Promise<ProductSummary[]> {
  const response = await getBeClient().get<PageResponse<ProductSummary>>('/products', {
    params: { page: 0, size: 5 },
  });
  return response.data.content;
}

export async function fetchNearbyFacilities(): Promise<FacilitySummary[]> {
  const response = await getBeClient().get<PageResponse<FacilitySummary>>('/facilities', {
    params: { page: 0, size: 5 },
  });
  return response.data.content;
}
