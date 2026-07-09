/**
 * U-01: getBookingDetail은 GET /bookings/{id}로 예약 상세를 반환한다(주문상세 Option A)
 * U-02: 존재하지 않는 예약(404)은 예외로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { getBookingDetail } from '../booking';
import type { BookingResponse } from '../types';

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  return { ...actual, getBeClient: jest.fn() };
});

import { getBeClient } from '../be-client';

const getBeClientMock = getBeClient as jest.MockedFunction<typeof getBeClient>;

describe('booking API — getBookingDetail', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  beforeEach(() => getBeClientMock.mockReturnValue(client));
  afterEach(() => mock.reset());

  const mockBooking: BookingResponse = {
    id: 42,
    slotId: 7,
    userId: 1,
    status: 'CONFIRMED',
    paymentId: 900,
    paymentStatus: 'PAID',
    createdAt: '2026-07-05T10:00:00.000Z',
    updatedAt: '2026-07-05T10:00:00.000Z',
  };

  describe('U-01', () => {
    it('GET /bookings/42 호출 시 예약 상세를 반환한다', async () => {
      mock.onGet('/bookings/42').reply(200, mockBooking);

      const res = await getBookingDetail(42);

      expect(res.id).toBe(42);
      expect(res.slotId).toBe(7);
      expect(res.paymentId).toBe(900);
    });
  });

  describe('U-02', () => {
    it('존재하지 않는 예약(404)은 예외로 전파된다', async () => {
      mock.onGet('/bookings/999').reply(404, { message: 'Not found' });

      await expect(getBookingDetail(999)).rejects.toThrow();
    });
  });
});
