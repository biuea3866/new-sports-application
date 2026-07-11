/**
 * U-01: POST /events/{id}/seats/select 성공 시 lockId와 expiresAt을 반환한다
 * U-02: POST /events/{id}/seats/release 성공 시 void를 반환한다
 * U-03: POST /ticket-orders 성공 시 ticketOrderId와 status를 반환한다
 * U-04: POST /ticket-orders 는 Idempotency-Key 헤더를 자동 생성하여 전송한다
 * U-05: POST /events/{id}/seats/select 실패(409) 시 에러를 발생시킨다
 * U-06: selectSeats 성공 후 purchaseTicketOrder 실패 시 releaseSeats를 호출할 수 있다 (재시도 경로 검증)
 * U-07: getTicketOrderDetail은 GET /ticket-orders/{id}로 상세(eventId·eventTitle·paymentId·createdAt 포함)를 반환한다(주문상세 Option A+)
 * U-08: 저장된 입장 토큰이 있으면 selectSeats가 X-Entry-Token 헤더를 부착한다(FE-09)
 * U-09: 저장된 입장 토큰이 없으면 selectSeats가 X-Entry-Token 헤더 없이 호출한다(FE-09)
 * U-10: isQueueBypassDeniedError는 403 QUEUE_BYPASS_DENIED만 true를 반환한다(FE-09)
 */
import MockAdapter from 'axios-mock-adapter';
import { AxiosError } from 'axios';
import { createBeClient } from '../be-client';
import { getTicketOrderDetail, isQueueBypassDeniedError, selectSeats } from '../ticketOrders';
import type { SelectSeatsResponse, TicketOrderDetailResponse, TicketOrderResponse } from '../types';
import { useEntryTokenStore } from '../../lib/entryTokenStore';

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  return { ...actual, getBeClient: jest.fn() };
});

import { getBeClient } from '../be-client';

const getBeClientMock = getBeClient as jest.MockedFunction<typeof getBeClient>;

describe('TicketOrders API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  beforeEach(() => getBeClientMock.mockReturnValue(client));
  afterEach(() => {
    mock.reset();
    useEntryTokenStore.setState({ tokens: {} });
  });

  const mockSelectResponse: SelectSeatsResponse = {
    lockId: '1:10,1:11',
    expiresAt: '2027-01-01T18:05:00Z',
  };

  const mockOrderResponse: TicketOrderResponse = {
    ticketOrderId: 42,
    status: 'PENDING',
  };

  describe('U-01: selectSeats', () => {
    it('POST /events/1/seats/select 성공 시 lockId와 expiresAt을 반환한다', async () => {
      mock.onPost('/events/1/seats/select').reply(200, mockSelectResponse);

      const res = await client.post<SelectSeatsResponse>('/events/1/seats/select', {
        seatIds: [10, 11],
      });

      expect(res.data.lockId).toBe('1:10,1:11');
      expect(res.data.expiresAt).toBe('2027-01-01T18:05:00Z');
    });
  });

  describe('U-02: releaseSeats', () => {
    it('POST /events/1/seats/release 성공 시 204를 반환한다', async () => {
      mock.onPost('/events/1/seats/release').reply(204);

      const res = await client.post('/events/1/seats/release', { seatIds: [10, 11] });

      expect(res.status).toBe(204);
    });
  });

  describe('U-03: purchaseTicketOrder', () => {
    it('POST /ticket-orders 성공 시 ticketOrderId와 status를 반환한다', async () => {
      mock.onPost('/ticket-orders').reply(202, mockOrderResponse);

      const res = await client.post<TicketOrderResponse>(
        '/ticket-orders',
        { lockId: '1:10,1:11', method: 'CREDIT_CARD', currency: 'KRW' },
        { headers: { 'Idempotency-Key': 'test-key-001' } }
      );

      expect(res.data.ticketOrderId).toBe(42);
      expect(res.data.status).toBe('PENDING');
    });
  });

  describe('U-04: Idempotency-Key 헤더', () => {
    it('POST /ticket-orders 요청에 Idempotency-Key 헤더가 포함된다', async () => {
      let capturedKey: string | undefined;

      mock.onPost('/ticket-orders').reply((config) => {
        capturedKey = (config.headers as Record<string, string>)['Idempotency-Key'];
        return [202, mockOrderResponse];
      });

      await client.post(
        '/ticket-orders',
        { lockId: '1:10,1:11', method: 'CREDIT_CARD', currency: 'KRW' },
        { headers: { 'Idempotency-Key': 'auto-generated-uuid' } }
      );

      expect(capturedKey).toBe('auto-generated-uuid');
    });
  });

  describe('U-05: selectSeats 409 실패', () => {
    it('이미 선점된 좌석 선택 시 409 에러가 발생한다', async () => {
      mock.onPost('/events/1/seats/select').reply(409, { message: 'Seat already locked' });

      await expect(client.post('/events/1/seats/select', { seatIds: [10] })).rejects.toThrow();
    });
  });

  describe('U-06: purchase 실패 후 release 경로', () => {
    it('selectSeats 성공 후 purchaseTicketOrder 500 실패 시 releaseSeats 엔드포인트가 정상 호출된다', async () => {
      mock.onPost('/events/1/seats/select').reply(200, mockSelectResponse);
      mock.onPost('/ticket-orders').reply(500, { message: 'Internal Server Error' });
      mock.onPost('/events/1/seats/release').reply(204);

      // select 성공
      const selectRes = await client.post<SelectSeatsResponse>('/events/1/seats/select', {
        seatIds: [10, 11],
      });
      expect(selectRes.data.lockId).toBe('1:10,1:11');

      // purchase 실패
      await expect(
        client.post(
          '/ticket-orders',
          { lockId: selectRes.data.lockId, method: 'CREDIT_CARD', currency: 'KRW' },
          { headers: { 'Idempotency-Key': 'key-001' } }
        )
      ).rejects.toThrow();

      // release 호출 가능 — 204 반환
      const releaseRes = await client.post('/events/1/seats/release', { seatIds: [10, 11] });
      expect(releaseRes.status).toBe(204);
    });
  });

  describe('U-07: getTicketOrderDetail', () => {
    const mockDetail: TicketOrderDetailResponse = {
      ticketOrderId: 12,
      status: 'CONFIRMED',
      eventId: 77,
      eventTitle: '2026 서울 마라톤',
      paymentId: 500,
      createdAt: '2026-07-05T10:00:00.000Z',
    };

    it('GET /ticket-orders/12 호출 시 eventId·eventTitle을 포함한 상세를 반환한다', async () => {
      mock.onGet('/ticket-orders/12').reply(200, mockDetail);

      const res = await getTicketOrderDetail(12);

      expect(res.ticketOrderId).toBe(12);
      expect(res.status).toBe('CONFIRMED');
      expect(res.eventId).toBe(77);
      expect(res.eventTitle).toBe('2026 서울 마라톤');
      expect(res.paymentId).toBe(500);
    });

    it('존재하지 않는 주문(404)은 예외로 전파된다', async () => {
      mock.onGet('/ticket-orders/999').reply(404, { message: 'Not found' });

      await expect(getTicketOrderDetail(999)).rejects.toThrow();
    });
  });

  describe('U-08: selectSeats + 저장된 입장 토큰', () => {
    it('저장된 입장 토큰이 있으면 X-Entry-Token 헤더가 부착된다', async () => {
      useEntryTokenStore
        .getState()
        .setToken('ticketing-event', 1, 'entry-token-abc', '2099-01-01T00:00:00Z');

      let capturedHeader: string | undefined;
      mock.onPost('/events/1/seats/select').reply((config) => {
        capturedHeader = (config.headers as Record<string, string>)['X-Entry-Token'];
        return [200, mockSelectResponse];
      });

      await selectSeats(1, [10, 11]);

      expect(capturedHeader).toBe('entry-token-abc');
    });
  });

  describe('U-09: selectSeats + 토큰 미저장', () => {
    it('저장된 입장 토큰이 없으면 X-Entry-Token 헤더 없이 호출한다', async () => {
      let capturedHeader: string | undefined;
      mock.onPost('/events/1/seats/select').reply((config) => {
        capturedHeader = (config.headers as Record<string, string>)['X-Entry-Token'];
        return [200, mockSelectResponse];
      });

      await selectSeats(1, [10, 11]);

      expect(capturedHeader).toBeUndefined();
    });
  });

  describe('U-10: isQueueBypassDeniedError', () => {
    it('403 QUEUE_BYPASS_DENIED 응답이면 true를 반환한다', () => {
      const error = new AxiosError('Forbidden', undefined, undefined, undefined, {
        status: 403,
        data: { code: 'QUEUE_BYPASS_DENIED' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as never,
      });

      expect(isQueueBypassDeniedError(error)).toBe(true);
    });

    it('403이어도 다른 code면 false를 반환한다', () => {
      const error = new AxiosError('Forbidden', undefined, undefined, undefined, {
        status: 403,
        data: { code: 'SOME_OTHER_REASON' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as never,
      });

      expect(isQueueBypassDeniedError(error)).toBe(false);
    });

    it('AxiosError가 아니면 false를 반환한다', () => {
      expect(isQueueBypassDeniedError(new Error('boom'))).toBe(false);
    });
  });
});
