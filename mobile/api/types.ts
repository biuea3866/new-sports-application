/**
 * BE API 응답 타입 정의
 * BE 컨트랙트에서 추출한 DTO 인터페이스.
 */
import type { SportCategory } from './community-types';

// --- Auth ---
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface RegisterUserResponse {
  id: number;
  email: string;
}

// --- User ---
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'BANNED';

export interface MyProfileResponse {
  id: number;
  email: string;
  status: UserStatus;
  createdAt: string; // ISO 8601
}

// --- Product ---
export type ProductCategory = 'EQUIPMENT' | 'APPAREL' | 'FOOTWEAR' | 'ACCESSORY';
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'SOLD_OUT';

export interface ProductSummary {
  id: number;
  name: string;
  category: ProductCategory;
  price: string; // BigDecimal → string
  imageUrl: string;
  status: ProductStatus;
  stockQuantity: number;
}

export interface ProductListResponse {
  content: ProductSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ProductDetailResponse {
  id: number;
  name: string;
  category: ProductCategory;
  price: string; // BigDecimal → string
  description: string;
  imageUrl: string;
  status: ProductStatus;
  stockQuantity: number;
}

// --- Booking ---
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export type PaymentStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'FAILED';

export interface BookingResponse {
  id: number;
  slotId: number;
  userId: number;
  status: BookingStatus;
  paymentId: number | null;
  paymentStatus: PaymentStatus | null;
  createdAt: string;
  updatedAt: string;
}

export interface ListBookingsResponse {
  bookings: BookingResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface CancelBookingRequest {
  reason?: string;
}

export interface CreateBookingRequest {
  slotId: number;
}

/** BE `domain/booking/entity/SlotStatus` — OPEN(예약 가능)/CLOSED(마감·운영자 수동 마감). */
export type SlotStatus = 'OPEN' | 'CLOSED';

// BE SlotResponse shape
export interface SlotResponse {
  id: number;
  facilityId: string;
  date: string; // ISO 8601
  timeRange: string;
  capacity: number;
  ownerId: number;
  /**
   * BE-59 additive 확장 필드(programId·status). 구 응답·기존 테스트 리터럴과 하위 호환을
   * 위해 optional로 선언한다(RoomResponse 확장과 동일 패턴).
   */
  status?: SlotStatus;
  /** 시설상품(program) 회차 슬롯이면 해당 program id, 일반 슬롯이면 null */
  programId?: number | null;
}

export type PaymentMethod = 'KAKAO' | 'TOSS' | 'NAVER' | 'DANAL' | 'CREDIT_CARD' | 'BANK_TRANSFER';

export interface CreateBookingBody {
  slotId: number;
  paymentMethod: PaymentMethod;
  amount: number;
  currency: 'KRW';
}

export interface CreateBookingResult {
  bookingId: number;
  slotId: number;
  userId: number;
  status: BookingStatus;
  paymentId: number;
}

// --- Facility ---
export type FacilityType = 'INDOOR' | 'OUTDOOR' | 'MIXED';

/** BE `domain/facility/vo/TimeRange` — 브레이크타임 등 시각 구간. */
export interface TimeRangeResponse {
  start: string;
  end: string;
}

/** BE `presentation/facility/dto/response/OperatingHoursResponse`. */
export interface OperatingHoursResponse {
  dayOfWeek: string;
  openTime: string;
  closeTime: string;
  breaks: TimeRangeResponse[];
  slotDurationMinutes: number;
  capacity: number;
}

export interface FacilityResponse {
  id: number;
  name: string;
  gu: string;
  type: FacilityType;
  address: string;
  parking: boolean;
  tel: string;
  lat: number;
  lng: number;
  sidoCode: string;
  sidoName: string;
  sigunguCode: string;
  sigunguName: string;
  /**
   * 운영시간·휴무는 `FacilityResponse.of(facility)`(BE)가 항상 포함하는 필드이지만,
   * 기존 테스트 리터럴(구 응답 시점)과 하위 호환을 위해 optional로 선언한다.
   */
  operatingHours?: OperatingHoursResponse[];
  /** 휴무일 목록(ISO-8601 LocalDate 문자열, 예: "2026-07-15") */
  holidays?: string[];
}

export interface FacilityPageResponse {
  content: FacilityResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// --- Air Quality ---
// BE 계약: GET /air-quality?lat&lng → 실패 시에도 200 + representativeGrade="UNKNOWN" + pm10/pm25 null.
export type AirQualityGrade = 'GOOD' | 'MODERATE' | 'BAD' | 'VERY_BAD' | 'UNKNOWN';

export interface AirQualityResponse {
  pm10: number | null;
  pm25: number | null;
  pm10Grade: AirQualityGrade;
  pm25Grade: AirQualityGrade;
  representativeGrade: AirQualityGrade;
  stationName: string | null;
  measuredAt: string | null; // ISO 8601
}

// --- Event ---
export type EventStatus = 'SCHEDULED' | 'OPEN' | 'CLOSED';

export interface EventResponse {
  id: number;
  title: string;
  venue: string;
  startsAt: string; // ISO 8601
  status: EventStatus;
}

export interface SectionAvailability {
  section: string;
  totalSeats: number;
}

export interface SeatInfo {
  id: number;
  section: string;
  rowNo: string;
  seatNo: string;
  price: string; // BigDecimal → string
  available: boolean;
}

export interface EventDetailResponse {
  id: number;
  title: string;
  venue: string;
  startsAt: string; // ISO 8601
  status: EventStatus;
  sections: SectionAvailability[];
  seats: SeatInfo[];
}

// --- TicketOrder ---
export type TicketOrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';

export interface SelectSeatsResponse {
  lockId: string;
  expiresAt: string; // ISO 8601
}

export interface PurchaseTicketOrderRequest {
  lockId: string;
  method: PaymentMethod;
  currency: string;
}

export interface TicketOrderResponse {
  ticketOrderId: number;
  status: TicketOrderStatus;
}

export interface ListEventsResponse {
  content: EventResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// --- Room / Message ---
export type RoomType = 'DIRECT' | 'GROUP';

// BE 계약(20260704-채팅시스템고도화-tdd.md "RoomResponse (확장)"): contextType/lastMessagePreview/
// lastMessageAt은 additive 확장 필드라 optional로 선언한다(구 응답·기존 테스트 리터럴과 하위 호환).
export interface RoomResponse {
  id: number;
  type: RoomType;
  name: string | null;
  /** 컨텍스트 없으면 null(기존 DIRECT/GROUP) */
  contextType?: 'COMMUNITY' | 'GOODS_PRODUCT' | null;
  /** 마지막 메시지 최대 50자. 메시지 없으면 null */
  lastMessagePreview?: string | null;
  /** 마지막 메시지 시각(ISO 8601). 없으면 null */
  lastMessageAt?: string | null;
}

export interface MessageResponse {
  id: number;
  roomId: number;
  senderId: number;
  content: string;
  sentAt: string; // ISO 8601
}

export interface ListMessagesResponse {
  messages: MessageResponse[];
  nextCursor: string | null;
}

export interface SendMessageRequest {
  content: string;
}

// --- Post ---
/** BE `domain/post/vo/PostType`. */
export type PostType = 'FREE' | 'NOTICE' | 'QUESTION' | 'REVIEW';

export interface PostResponse {
  id: number;
  userId: number;
  title: string;
  type: PostType;
  createdAt: string; // ISO 8601
  /**
   * BE-23/25(post-community 연동) additive 확장 필드. 구 응답·기존 테스트 리터럴과
   * 하위 호환을 위해 optional로 선언한다(RoomResponse 확장과 동일 패턴).
   * null/미소속이면 전역 게시글.
   */
  communityId?: number | null;
  /** 모임 게시글은 소속 모임 종목을 상속, 전역 게시글은 작성 시 선택(없으면 null) */
  sportCategory?: SportCategory | null;
}

export interface CommentResponse {
  id: number;
  postId: number;
  userId: number;
  content: string;
  createdAt: string; // ISO 8601
}

/** BE `presentation/post/dto/response/CommentPageResponse` — PageResponse와 필드명(page vs number)이 다르다. */
export interface CommentPageResponse {
  content: CommentResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface PostDetailResponse {
  id: number;
  userId: number;
  title: string;
  content: string;
  type: PostType;
  createdAt: string; // ISO 8601
  comments: CommentResponse[];
  communityId?: number | null;
  sportCategory?: SportCategory | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-indexed)
  size: number;
}

export interface CreatePostRequest {
  title: string;
  content: string;
  type?: PostType;
  /** 지정 시 모임 게시글(`CreateCommunityPostUseCase` 경로)로 생성된다 */
  communityId?: number | null;
  /** 모임 게시글은 BE가 소속 모임 값을 상속하므로 무시된다(FR-5) — 전역 게시글에서만 유효 */
  sportCategory?: SportCategory | null;
}

/** `GET /posts` 쿼리 파라미터(page/size 제외) — BE `PostApiController#searchPosts`. */
export interface PostSearchParams {
  type?: PostType;
  userId?: number;
  keyword?: string;
  communityId?: number;
  sportCategory?: SportCategory;
}

/** `GET /communities/{communityId}/posts` 쿼리 파라미터(page/size 제외). */
export interface CommunityPostSearchParams {
  sportCategory?: SportCategory;
}

// --- Cart ---
export interface CartItemResponse {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string;
  unitPrice: string; // BigDecimal → string
  quantity: number;
  subtotal: string; // BigDecimal → string
}

export interface CartResponse {
  id: number;
  userId: number;
  items: CartItemResponse[];
  totalAmount: string; // BigDecimal → string
}

export interface AddCartItemRequest {
  productId: number;
  quantity: number;
}

export interface UpdateCartItemRequest {
  quantity: number;
}

// --- GoodsOrder ---
export type GoodsOrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED';

export interface GoodsOrderItemResponse {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: string;
  subtotal: string;
}

export interface GoodsOrderResponse {
  id: number;
  userId: number;
  status: GoodsOrderStatus;
  items: GoodsOrderItemResponse[];
  totalAmount: string;
  createdAt: string; // ISO 8601
}

export interface GoodsOrderListResponse {
  content: GoodsOrderResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateGoodsOrderRequest {
  cartId: number;
}

// --- Notification ---
export type NotificationType = 'BOOKING' | 'PAYMENT' | 'EVENT' | 'SYSTEM' | 'PROMOTION';

export interface NotificationResponse {
  id: number;
  title: string;
  content: string;
  type: NotificationType;
  isRead: boolean;
  readAt: string | null; // ISO 8601
  createdAt: string; // ISO 8601
}

export interface NotificationListResponse {
  content: NotificationResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

// --- LimitedDrop ---
export type LimitedDropStatus = 'SCHEDULED' | 'OPEN' | 'SOLD_OUT' | 'CLOSED';

export interface LimitedDropResponse {
  dropId: number;
  productId: number;
  status: LimitedDropStatus;
  openAt: string; // ISO 8601
  closeAt: string; // ISO 8601
  remaining: number;
  perUserLimit: number;
  /** 회차 전체 한정 수량. remaining과 결합해 재고비율 바 표시에 사용한다. */
  totalQuantity: number;
  /** 상품 단가(BigDecimal → JSON number). 결제 amount 전달에 사용한다. */
  price: number;
}

export interface PurchaseLimitedDropRequest {
  quantity: number;
}

export type LimitedDropOrderStatus = 'PENDING';

export interface LimitedDropPurchaseResponse {
  orderId: number;
  dropId: number;
  status: LimitedDropOrderStatus;
}

export interface LimitedDropApiErrorBody {
  code?: string;
  message?: string;
  openAt?: string; // 425 TooEarly 응답에 포함
}

/**
 * 구매 요청의 판별 가능한 결과.
 * - ADMITTED: 202 성공 — 주문 PENDING 선점
 * - TOO_EARLY: 425 — openAt 이전 요청
 * - SOLD_OUT / CLOSED: 409 — 소진 또는 회차 종료
 *   (에러 바디 code가 LIMITED_DROP_CLOSED면 CLOSED, 그 외(LIMITED_DROP_SOLD_OUT 포함·없음)는 SOLD_OUT)
 * - THROTTLED: 429 — 완충 초과
 * - LIMIT_EXCEEDED: 403 — 1인 한도 초과
 */
export type LimitedDropPurchaseResult =
  | { outcome: 'ADMITTED'; data: LimitedDropPurchaseResponse }
  | { outcome: 'TOO_EARLY'; openAt: string | null }
  | { outcome: 'SOLD_OUT' }
  | { outcome: 'CLOSED' }
  | { outcome: 'THROTTLED' }
  | { outcome: 'LIMIT_EXCEEDED' };
