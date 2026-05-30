/**
 * U-01: GET /facilities/{facilityId}/slots 성공 시 슬롯 목록을 반환한다
 * U-02: GET /facilities/{facilityId}/slots 실패(404) 시 에러가 발생한다
 * U-03: POST /bookings 성공 시 CreateBookingResult를 반환한다
 * U-04: POST /bookings 실패(400) 시 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../../api/be-client';
import type { CreateBookingResult, SlotResponse } from '../../api/types';

describe('Booking Slot API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockSlot: SlotResponse = {
    id: 1,
    facilityId: 'facility-1',
    date: '2026-06-01T09:00:00Z',
    timeRange: '09:00 - 10:00',
    capacity: 20,
    ownerId: 42,
  };

  describe('U-01: listSlots', () => {
    it('GET /facilities/facility-1/slots 호출 시 슬롯 목록을 반환한다', async () => {
      mock.onGet('/facilities/facility-1/slots').reply(200, [mockSlot]);

      const res = await client.get<SlotResponse[]>('/facilities/facility-1/slots');

      expect(res.data).toHaveLength(1);
      expect(res.data[0].id).toBe(1);
      expect(res.data[0].timeRange).toBe('09:00 - 10:00');
      expect(res.data[0].capacity).toBe(20);
    });
  });

  describe('U-02: listSlots 없는 시설', () => {
    it('GET /facilities/unknown/slots 호출 시 404 에러가 발생한다', async () => {
      mock.onGet('/facilities/unknown/slots').reply(404, { message: 'Not found' });

      await expect(client.get('/facilities/unknown/slots')).rejects.toThrow();
    });
  });

  describe('U-03: createBooking', () => {
    it('POST /bookings 호출 시 CreateBookingResult를 반환한다', async () => {
      const mockResult: CreateBookingResult = {
        bookingId: 100,
        slotId: 1,
        userId: 42,
        status: 'PENDING',
        paymentId: 200,
      };

      mock.onPost('/bookings').reply(202, mockResult);

      const res = await client.post<CreateBookingResult>('/bookings', {
        slotId: 1,
        paymentMethod: 'CREDIT_CARD',
        amount: 10000,
        currency: 'KRW',
      });

      expect(res.data.bookingId).toBe(100);
      expect(res.data.status).toBe('PENDING');
      expect(res.data.paymentId).toBe(200);
    });
  });

  describe('U-04: createBooking 실패', () => {
    it('POST /bookings 400 응답 시 에러가 발생한다', async () => {
      mock.onPost('/bookings').reply(400, { message: 'Invalid slot' });

      await expect(
        client.post('/bookings', {
          slotId: 999,
          paymentMethod: 'CREDIT_CARD',
          amount: 10000,
          currency: 'KRW',
        })
      ).rejects.toThrow();
    });
  });
});
