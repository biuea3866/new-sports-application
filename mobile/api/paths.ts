/**
 * paths.ts — BE API 경로 상수
 *
 * 모든 도메인 API 함수는 이 파일의 상수를 사용합니다.
 * 경로 하드코딩 금지.
 */

export const PATHS = {
  // 시설
  facilities: '/facilities',
  facilityById: (id: number) => `/facilities/${id}`,
  facilitySlots: (id: number) => `/facilities/${id}/slots`,

  // 예약
  bookings: '/bookings',
  bookingsMe: '/bookings/me',
  bookingById: (id: number) => `/bookings/${id}`,
  bookingCancel: (id: number) => `/bookings/${id}/cancel`,

  // 상품
  products: '/products',
  productsPopular: '/products/popular',
  productById: (id: number) => `/products/${id}`,

  // 장바구니
  cartMe: '/cart/me',
  cartItems: '/cart/items',
  cartItemById: (id: number) => `/cart/items/${id}`,
  cart: '/cart',

  // 상품 주문
  goodsOrders: '/goods-orders',
  goodsOrderById: (id: number) => `/goods-orders/${id}`,
  goodsOrdersMe: '/goods-orders/me',

  // 이벤트
  events: '/events',
  eventById: (id: number) => `/events/${id}`,
  eventSeatSelect: (id: number) => `/events/${id}/seats/select`,
  eventSeatRelease: (id: number) => `/events/${id}/seats/release`,

  // 티켓 주문
  ticketOrders: '/ticket-orders',

  // 결제
  payments: '/payments',
  paymentsMe: '/payments/me',
  paymentById: (id: number) => `/payments/${id}`,

  // 알림
  notificationsMe: '/notifications/me',
  notificationsUnreadCount: '/notifications/me/unread-count',
  notificationRead: (id: number) => `/notifications/${id}/read`,

  // 채팅방
  rooms: '/rooms',
  roomById: (id: number) => `/rooms/${id}`,
  roomMessages: (id: number) => `/rooms/${id}/messages`,

  // 커뮤니티 게시글
  posts: '/posts',
  postById: (id: number) => `/posts/${id}`,
  postComments: (postId: number) => `/posts/${postId}/comments`,
  postCommentById: (postId: number, commentId: number) =>
    `/posts/${postId}/comments/${commentId}`,

  // 사용자
  usersMe: '/users/me',
  usersRegister: '/users/register',
} as const;
