/**
 * queryKeys.ts — TanStack Query 키 팩토리
 *
 * 모든 도메인의 query key는 이 파일에서 중앙 관리합니다.
 * 배열 구조로 계층적 무효화가 가능합니다.
 *
 * 예: queryClient.invalidateQueries({ queryKey: facilitiesKeys.all })
 *     → facilities 관련 모든 캐시 무효화
 */

// 파라미터 객체를 query key에 담기 위한 공통 타입
type ListParams = object;

export const facilitiesKeys = {
  all: ['facilities'] as const,
  lists: () => [...facilitiesKeys.all, 'list'] as const,
  list: (params: ListParams) => [...facilitiesKeys.lists(), params] as const,
  details: () => [...facilitiesKeys.all, 'detail'] as const,
  detail: (id: number) => [...facilitiesKeys.details(), id] as const,
  slots: (id: number, date: string) => [...facilitiesKeys.detail(id), 'slots', date] as const,
};

export const bookingsKeys = {
  all: ['bookings'] as const,
  mine: () => [...bookingsKeys.all, 'me'] as const,
  myList: (params: ListParams) => [...bookingsKeys.mine(), params] as const,
  details: () => [...bookingsKeys.all, 'detail'] as const,
  detail: (id: number) => [...bookingsKeys.details(), id] as const,
};

export const productsKeys = {
  all: ['products'] as const,
  lists: () => [...productsKeys.all, 'list'] as const,
  list: (params: ListParams) => [...productsKeys.lists(), params] as const,
  popular: () => [...productsKeys.all, 'popular'] as const,
  details: () => [...productsKeys.all, 'detail'] as const,
  detail: (id: number) => [...productsKeys.details(), id] as const,
};

export const cartKeys = {
  all: ['cart'] as const,
  mine: () => [...cartKeys.all, 'me'] as const,
};

export const goodsOrdersKeys = {
  all: ['goodsOrders'] as const,
  mine: () => [...goodsOrdersKeys.all, 'me'] as const,
  myList: (params: ListParams) => [...goodsOrdersKeys.mine(), params] as const,
  details: () => [...goodsOrdersKeys.all, 'detail'] as const,
  detail: (id: number) => [...goodsOrdersKeys.details(), id] as const,
};

export const eventsKeys = {
  all: ['events'] as const,
  lists: () => [...eventsKeys.all, 'list'] as const,
  list: (params: ListParams) => [...eventsKeys.lists(), params] as const,
  details: () => [...eventsKeys.all, 'detail'] as const,
  detail: (id: number) => [...eventsKeys.details(), id] as const,
};

export const paymentsKeys = {
  all: ['payments'] as const,
  mine: () => [...paymentsKeys.all, 'me'] as const,
  myList: (params: ListParams) => [...paymentsKeys.mine(), params] as const,
  details: () => [...paymentsKeys.all, 'detail'] as const,
  detail: (id: number) => [...paymentsKeys.details(), id] as const,
};

export const notificationsKeys = {
  all: ['notifications'] as const,
  mine: () => [...notificationsKeys.all, 'me'] as const,
  myList: (params: ListParams) => [...notificationsKeys.mine(), params] as const,
  unreadCount: () => [...notificationsKeys.all, 'unreadCount'] as const,
};

export const roomsKeys = {
  all: ['rooms'] as const,
  mine: () => [...roomsKeys.all, 'me'] as const,
  messages: (id: number) => [...roomsKeys.all, id, 'messages'] as const,
};

export const postsKeys = {
  all: ['posts'] as const,
  lists: () => [...postsKeys.all, 'list'] as const,
  list: (params: ListParams) => [...postsKeys.lists(), params] as const,
  details: () => [...postsKeys.all, 'detail'] as const,
  detail: (id: number) => [...postsKeys.details(), id] as const,
  comments: (postId: number) => [...postsKeys.detail(postId), 'comments'] as const,
};

export const usersKeys = {
  all: ['users'] as const,
  me: () => [...usersKeys.all, 'me'] as const,
};
