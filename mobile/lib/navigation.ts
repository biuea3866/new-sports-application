/**
 * 앱 라우트 상수 — 후행 티켓이 경로 문자열을 하드코딩하지 않도록 한다.
 *
 * 탭 재편(사용자 피드백, 7탭→5탭)으로 `tickets`·`chat`·`clubs` 탭이 사라지고
 * 스토어(굿즈|티켓)·커뮤니티(게시글|동아리) 세그먼트로 통합됐다. `search`는
 * 실제 기능(내 주변 시설 검색)을 드러내는 `facilities`로 재명명했다.
 */

export const ROUTES = {
  // 탭
  tabs: {
    home: '/(tabs)/',
    facilities: '/(tabs)/facilities',
    store: '/(tabs)/store',
    /** 스토어 탭을 티켓 세그먼트로 바로 여는 진입점 (예: 홈 "다가오는 경기" 카드). */
    storeTickets: '/(tabs)/store?segment=tickets',
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

  // 한정판
  limitedDrop: {
    detail: (id: string) => `/limited-drop/${id}` as const,
    purchase: (id: string) => `/limited-drop/${id}/purchase` as const,
  },

  // 모집(recruitment) — `app/recruitments/[id].tsx` 실제 라우트와 일치.
  recruitment: {
    detail: (id: string) => `/recruitments/${id}` as const,
  },

  // 통합 상품 검색(FE-09) / 통합 주문 내역(FE-10) — FE-11 와이어업
  catalog: '/catalog',
  orders: '/orders',

  // 주문 상세(Option A) — 통합 주문내역 항목 탭 시 "주문 자신"의 상세로 이동한다.
  // `orderType`은 `api/order-history-types.ts`의 `OrderType`.
  orderDetail: (orderType: string, id: string) => `/orders/${orderType}/${id}` as const,

  // 가상 대기열 대기실(FE-04) — `type`은 BE 계약과 일치하는 `limited-drop` | `ticketing-event`.
  // 후행 진입점 와이어업 티켓(FE-07·08·09)은 이 빌더로만 이동한다.
  queue: {
    waiting: (type: string, targetId: string) => `/queue/${type}/${targetId}` as const,
  },
} as const;
