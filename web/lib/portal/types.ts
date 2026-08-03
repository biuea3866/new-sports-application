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
  sidoCode: string;
  sidoName: string;
  sigunguCode: string;
  sigunguName: string;
  type: FacilityType;
  address: string;
  lat: number;
  lng: number;
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
  /** 시/도 표준코드 (2자리). optional — 미입력 시 서버가 주소로 보간한다. */
  sido?: string;
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
  // 시/도 표준코드. 미입력 시 서버가 address로 자동 판별한다.
  sido?: string;
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

/**
 * 경기 단건 상세 — BE `/api/event-host/events/{id}`(MyEventWithSalesResponse) 응답 형태.
 * 목록 응답(MyEvent)과 필드 구성이 다르므로 확장하지 않고 별도로 정의한다.
 */
export interface MyEventDetail {
  id: number;
  title: string;
  venue: string;
  startsAt: string;
  status: EventStatus;
  totalSeats: number;
  totalSold: number;
  totalAvailable: number;
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
export type ProductCategory = "EQUIPMENT" | "APPAREL" | "FOOTWEAR" | "ACCESSORY";

export interface MyProduct {
  id: number;
  name: string;
  description: string;
  price: number;
  category: ProductCategory;
  imageUrl: string;
  status: ProductStatus;
  stockQuantity: number;
  // 목록 응답에는 없을 수 있다 (상세 응답에만 포함).
  ownerId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateProductInput {
  name: string;
  description: string;
  price: number;
  category: ProductCategory;
  imageUrl: string;
}

export interface UpdateProductInput {
  name?: string;
  description?: string;
  price?: number;
  category?: ProductCategory;
  imageUrl?: string;
}

export interface RestoreStockInput {
  quantity: number;
}

// ─── AdminUser ───────────────────────────────────────────────────────────────

export type UserStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";

export interface AdminUser {
  userId: number;
  email: string;
  status: UserStatus;
  roleNames: string[];
  joinedAt: string;
}


// ─── Notification ────────────────────────────────────────────────────────────

/**
 * 알림함 분류 — BE `domain/notification/vo/NotificationCategory`.
 * templateId 에서 파생하는 표시용 값이라 BE가 미등록 접두사를 SYSTEM 으로 수렴시킨다.
 */
export type NotificationCategory = "BOOKING" | "PAYMENT" | "EVENT" | "SYSTEM" | "PROMOTION";

/**
 * 알림함 한 줄 — BE `MyNotificationResponse` 계약 그대로.
 *
 * BE는 발송 내부 값(channel·templateId·status·sentAt)을 이 응답에 싣지 않는다. 그 필드들을
 * 기대하던 옛 타입이 남아 있어 카드가 통째로 비어 보인 이력이 있다(02-파트너포털/17 캡쳐).
 */
export interface Notification {
  id: number;
  title: string;
  content: string;
  category: NotificationCategory;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPage {
  content: Notification[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}


// ─── Facility Schedule (운영시간 / 휴무일) ─────────────────────────────────────
// BE 계약: FacilityScheduleApiController — PUT/POST/DELETE .../operating-hours,
// .../holidays. 운영시간·휴무는 시설 상세 응답(FacilityResponse)에 임베드된다.

export type DayOfWeek =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export interface TimeRange {
  start: string;
  end: string;
}

export interface OperatingHours {
  dayOfWeek: DayOfWeek;
  openTime: string;
  closeTime: string;
  breaks: TimeRange[];
  slotDurationMinutes: number;
  capacity: number;
}

export interface FacilitySchedule {
  id: string;
  operatingHours: OperatingHours[];
  holidays: string[];
}

export interface RegisterOperatingHoursInput {
  operatingHours: OperatingHours[];
}

// ─── Program (시설상품) ─────────────────────────────────────────────────────────
// BE 계약: ProgramApiController — POST/GET /facilities/{facilityId}/programs

export interface Program {
  id: number;
  facilityId: string;
  ownerUserId: number;
  name: string;
  description: string | null;
  price: number;
  capacity: number;
  durationMinutes: number;
}

export interface CreateProgramInput {
  name: string;
  description?: string;
  price: number;
  capacity: number;
  durationMinutes: number;
}

// ─── Slot 상태 (open/close) ─────────────────────────────────────────────────────
// BE 계약: SlotApiController — PATCH .../slots/{slotId}/close · /open

export type SlotStatus = "OPEN" | "CLOSED";

export interface Slot {
  id: number;
  facilityId: string;
  date: string;
  timeRange: string;
  capacity: number;
  ownerId: number;
  status: SlotStatus;
  programId: number | null;
}

// ─── Dashboard ───────────────────────────────────────────────────────────────

export interface FacilitySummary {
  count: number;
  activeSlotsToday: number;
}

export interface EventSummary {
  scheduled: number;
  open: number;
  closed: number;
  totalSeats: number;
  soldSeats: number;
}

export interface ProductSummary {
  active: number;
  outOfStock: number;
}

export interface DashboardSummary {
  facilities: FacilitySummary | null;
  events: EventSummary | null;
  products: ProductSummary | null;
}

