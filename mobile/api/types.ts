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
export type ProductCategory = string;
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'SOLD_OUT';

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
