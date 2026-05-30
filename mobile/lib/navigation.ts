/**
 * 앱 라우트 상수 — 후행 티켓이 경로 문자열을 하드코딩하지 않도록 한다.
 */

export const ROUTES = {
  // 탭
  tabs: {
    home: '/(tabs)/',
    store: '/(tabs)/store',
    tickets: '/(tabs)/tickets',
    community: '/(tabs)/community',
    me: '/(tabs)/me',
  },

  // 시설
  facility: {
    detail: (id: string) => `/facility/${id}` as const,
  },

  // 예약
  booking: {
    list: '/booking',
    new: '/booking/new',
  },

  // 상품
  product: {
    detail: (id: string) => `/product/${id}` as const,
  },

  // 장바구니 / 주문
  cart: '/cart',
  order: {
    list: '/order',
    new: '/order/new',
  },

  // 이벤트
  event: {
    detail: (id: string) => `/event/${id}` as const,
    order: (id: string) => `/event/${id}/order` as const,
  },

  // 결제
  payment: {
    list: '/payment',
    new: '/payment/new',
    detail: (id: string) => `/payment/${id}` as const,
  },

  // 알림
  notifications: '/notifications',

  // 채팅
  rooms: {
    list: '/rooms',
    detail: (id: string) => `/rooms/${id}` as const,
  },

  // 커뮤니티
  community: {
    list: '/community',
    detail: (id: string) => `/community/${id}` as const,
    new: '/community/new',
  },
} as const;
