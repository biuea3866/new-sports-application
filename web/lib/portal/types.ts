/**
 * B2B Portal 도메인 타입 정의.
 * BE API Response DTO와 1:1 매핑한다.
 */

// ─── 공통 ───────────────────────────────────────────────────────────────────

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
}

// ─── Facility ────────────────────────────────────────────────────────────────

export type FacilityType = "INDOOR" | "OUTDOOR" | "MIXED";

export interface MyFacility {
  id: string;
  code: string;
  name: string;
  gu: string;
  type: FacilityType;
  address: string;
  location: string;
  parking: boolean;
  tel: string;
  homePage: string | null;
  eduYn: boolean;
  meta: string | null;
  ownerUserId: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFacilityInput {
  code: string;
  name: string;
  gu: string;
  type: FacilityType;
  address: string;
  location: string;
  parking: boolean;
  tel: string;
  homePage?: string;
  eduYn: boolean;
  meta?: string;
}

export interface UpdateFacilityInput {
  name?: string;
  gu?: string;
  type?: FacilityType;
  address?: string;
  location?: string;
  parking?: boolean;
  tel?: string;
  homePage?: string;
  eduYn?: boolean;
  meta?: string;
}

// ─── Event ───────────────────────────────────────────────────────────────────

export type EventStatus = "SCHEDULED" | "OPEN" | "CLOSED" | "CANCELLED";

export interface SeatInfo {
  id: number;
  label: string;
  sold: boolean;
}

export interface MyEvent {
  id: number;
  title: string;
  venue: string;
  startsAt: string;
  status: EventStatus;
  ownerId: number;
  totalSeats: number;
  soldSeats: number;
  availableSeats: number;
  createdAt: string;
  updatedAt: string;
}

export interface MyEventDetail extends MyEvent {
  seats: SeatInfo[];
}

export interface CreateEventInput {
  title: string;
  venue: string;
  startsAt: string;
  seats: string[];
}

// ─── Product ─────────────────────────────────────────────────────────────────

export type ProductStatus = "ACTIVE" | "INACTIVE";

export interface MyProduct {
  id: number;
  name: string;
  description: string;
  price: number;
  status: ProductStatus;
  stockQuantity: number;
  ownerId: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductInput {
  name: string;
  description: string;
  price: number;
}

export interface UpdateProductInput {
  name?: string;
  description?: string;
  price?: number;
}

export interface RestoreStockInput {
  quantity: number;
}

// ─── Dashboard ───────────────────────────────────────────────────────────────

export interface FacilitySummary {
  totalFacilities: number;
  totalSlots: number;
}

export interface EventSummary {
  totalEvents: number;
  scheduledEvents: number;
  openEvents: number;
}

export interface ProductSummary {
  totalProducts: number;
  activeProducts: number;
  outOfStockProducts: number;
}

export interface DashboardSummary {
  facilities: FacilitySummary | null;
  events: EventSummary | null;
  products: ProductSummary | null;
}
