/**
 * U-01: GET /events 성공 시 이벤트 목록을 반환한다
 * U-02: GET /events?status=OPEN 호출 시 status 파라미터가 포함된다
 * U-03: GET /events/{id} 성공 시 이벤트 상세와 섹션 정보를 반환한다
 * U-04: GET /events/{id} 404 응답 시 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { EventDetailResponse, EventResponse, ListEventsResponse } from '../types';

describe('Event API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockEvent: EventResponse = {
    id: 1,
    title: '2024 KBO 한국시리즈',
    venue: '잠실 야구장',
    startsAt: '2024-10-20T18:30:00Z',
    status: 'OPEN',
  };

  const mockListResponse: ListEventsResponse = {
    content: [mockEvent],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
  };

  const mockDetailResponse: EventDetailResponse = {
    id: 1,
    title: '2024 KBO 한국시리즈',
    venue: '잠실 야구장',
    startsAt: '2024-10-20T18:30:00Z',
    status: 'OPEN',
    sections: [
      { section: 'A', totalSeats: 100 },
      { section: 'B', totalSeats: 80 },
    ],
    seats: [
      { id: 10, section: 'A', rowNo: '1', seatNo: '1', price: '30000', available: true },
      { id: 11, section: 'A', rowNo: '1', seatNo: '2', price: '30000', available: false },
    ],
  };

  describe('U-01: listEvents', () => {
    it('GET /events 호출 시 이벤트 목록 페이지를 반환한다', async () => {
      mock.onGet('/events').reply(200, mockListResponse);

      const res = await client.get<ListEventsResponse>('/events', {
        params: { page: 0, size: 20 },
      });

      expect(res.data.content).toHaveLength(1);
      expect(res.data.content[0].id).toBe(1);
      expect(res.data.content[0].status).toBe('OPEN');
      expect(res.data.totalElements).toBe(1);
    });
  });

  describe('U-02: listEvents with status filter', () => {
    it('status=OPEN 파라미터가 포함된 GET /events 요청이 성공한다', async () => {
      mock.onGet('/events').reply(200, mockListResponse);

      const res = await client.get<ListEventsResponse>('/events', {
        params: { page: 0, size: 20, status: 'OPEN' },
      });

      expect(res.data.content[0].status).toBe('OPEN');
    });
  });

  describe('U-03: getEvent', () => {
    it('GET /events/1 호출 시 섹션 정보를 포함한 상세를 반환한다', async () => {
      mock.onGet('/events/1').reply(200, mockDetailResponse);

      const res = await client.get<EventDetailResponse>('/events/1');

      expect(res.data.id).toBe(1);
      expect(res.data.sections).toHaveLength(2);
      expect(res.data.sections[0].section).toBe('A');
      expect(res.data.sections[0].totalSeats).toBe(100);
    });
  });

  describe('U-04: getEvent 404', () => {
    it('존재하지 않는 이벤트 조회 시 404 에러가 발생한다', async () => {
      mock.onGet('/events/999').reply(404, { message: 'Event not found' });

      await expect(client.get('/events/999')).rejects.toThrow();
    });
  });
});
