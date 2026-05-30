/**
 * BE API 응답 타입 정의
 * BE 컨트랙트에서 추출한 DTO 인터페이스.
 */

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

// BE SlotResponse shape
export interface SlotResponse {
  id: number;
  facilityId: string;
  date: string; // ISO 8601
  timeRange: string;
  capacity: number;
  ownerId: number;
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

export interface FacilityResponse {
  id: number;
  name: string;
  gu: string;
  type: FacilityType;
  address: string;
  parking: boolean;
  phone: string;
}

export interface FacilityPageResponse {
  content: FacilityResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
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

export interface EventDetailResponse {
  id: number;
  title: string;
  venue: string;
  startsAt: string; // ISO 8601
  status: EventStatus;
  sections: SectionAvailability[];
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

export interface RoomResponse {
  id: number;
  type: RoomType;
  name: string | null;
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
export type PostType = string;

export interface PostResponse {
  id: number;
  userId: number;
  title: string;
  type: PostType;
  createdAt: string; // ISO 8601
}

export interface CommentResponse {
  id: number;
  postId: number;
  userId: number;
  content: string;
  createdAt: string; // ISO 8601
}

export interface PostDetailResponse {
  id: number;
  userId: number;
  title: string;
  content: string;
  type: PostType;
  createdAt: string; // ISO 8601
  comments: CommentResponse[];
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
