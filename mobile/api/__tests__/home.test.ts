/**
 * U-01: fetchUpcomingEvents — /events 호출 시 content 배열을 반환한다
 * U-02: fetchRecommendedProducts — /products 호출 시 content 배열을 반환한다
 * U-03: fetchNearbyFacilities — /facilities 호출 시 content 배열을 반환한다
 * U-04: API 오류 시 에러가 그대로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { EventSummary, ProductSummary, FacilitySummary } from '../home';

// be-client singleton 대신 테스트용 인스턴스를 주입하기 위해 모듈을 mock 처리
jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  return {
    ...actual,
    getBeClient: jest.fn(),
  };
});

import { getBeClient } from '../be-client';
import { fetchUpcomingEvents, fetchRecommendedProducts, fetchNearbyFacilities } from '../home';

const mockedGetBeClient = jest.mocked(getBeClient);

function makePageResponse<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 5,
  };
}

describe('home API', () => {
  let mockAdapter: MockAdapter;

  beforeEach(() => {
    const instance = createBeClient('http://localhost:8080');
    mockAdapter = new MockAdapter(instance);
    mockedGetBeClient.mockReturnValue(instance);
  });

  afterEach(() => {
    mockAdapter.restore();
    jest.clearAllMocks();
  });

  describe('U-01: fetchUpcomingEvents', () => {
    it('/events?page=0&size=5 를 호출하고 content 배열을 반환한다', async () => {
      const events: EventSummary[] = [
        {
          id: 1,
          title: '스포츠 페스티벌',
          startAt: '2026-06-01T10:00:00Z',
          endAt: '2026-06-01T18:00:00Z',
          location: '서울 올림픽공원',
          thumbnailUrl: 'https://example.com/thumb1.jpg',
        },
      ];
      mockAdapter.onGet('/events').reply(200, makePageResponse(events));

      const result = await fetchUpcomingEvents();

      expect(result).toEqual(events);
      expect(mockAdapter.history.get[0].params).toEqual({ page: 0, size: 5 });
    });

    it('content가 빈 배열이면 빈 배열을 반환한다', async () => {
      mockAdapter.onGet('/events').reply(200, makePageResponse([]));

      const result = await fetchUpcomingEvents();

      expect(result).toEqual([]);
    });
  });

  describe('U-02: fetchRecommendedProducts', () => {
    it('/products?page=0&size=5 를 호출하고 content 배열을 반환한다', async () => {
      const products: ProductSummary[] = [
        { id: 10, name: '테니스 라켓', price: 120000, thumbnailUrl: null },
        { id: 11, name: '배드민턴 셔틀콕', price: 15000, thumbnailUrl: 'https://example.com/t2.jpg' },
      ];
      mockAdapter.onGet('/products').reply(200, makePageResponse(products));

      const result = await fetchRecommendedProducts();

      expect(result).toEqual(products);
      expect(mockAdapter.history.get[0].params).toEqual({ page: 0, size: 5 });
    });
  });

  describe('U-03: fetchNearbyFacilities', () => {
    it('/facilities?page=0&size=5 를 호출하고 content 배열을 반환한다', async () => {
      const facilities: FacilitySummary[] = [
        { id: 20, name: '강남 스포츠센터', address: '서울 강남구', thumbnailUrl: null },
      ];
      mockAdapter.onGet('/facilities').reply(200, makePageResponse(facilities));

      const result = await fetchNearbyFacilities();

      expect(result).toEqual(facilities);
      expect(mockAdapter.history.get[0].params).toEqual({ page: 0, size: 5 });
    });
  });

  describe('U-04: API 오류 전파', () => {
    it('서버 500 응답 시 에러가 전파된다', async () => {
      mockAdapter.onGet('/events').reply(500, { message: 'Internal Server Error' });

      await expect(fetchUpcomingEvents()).rejects.toThrow();
    });

    it('네트워크 오류 시 에러가 전파된다', async () => {
      mockAdapter.onGet('/products').networkError();

      await expect(fetchRecommendedProducts()).rejects.toThrow();
    });
  });
});
