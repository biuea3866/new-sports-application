/**
 * U-01: GET /bookings/me 성공 시 예약 목록을 반환한다
 * U-02: POST /bookings/{id}/cancel 성공 시 취소된 예약을 반환한다
 * U-03: 존재하지 않는 예약 취소 시 404 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../../api/be-client';
import type { BookingResponse, ListBookingsResponse } from '../../api/types';

// expo-secure-store, expo-router는 jest.setup.ts의 global mock에서 처리

describe('Booking API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockBooking: BookingResponse = {
    id: 10,
    slotId: 1,
    facilityId: null,
    userId: 42,
    status: 'CONFIRMED',
    paymentId: null,
    paymentStatus: null,
    title: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  describe('U-01: listMyBookings', () => {
    it('GET /bookings/me 호출 시 예약 목록을 반환한다', async () => {
      const mockResponse: ListBookingsResponse = {
        bookings: [mockBooking],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 20,
      };
      mock.onGet('/bookings/me').reply(200, mockResponse);

      const res = await client.get<ListBookingsResponse>('/bookings/me', {
        params: { page: 0, size: 20 },
      });

      expect(res.data.bookings).toHaveLength(1);
      expect(res.data.bookings[0].id).toBe(10);
      expect(res.data.totalElements).toBe(1);
    });
  });

  describe('U-02: cancelBooking', () => {
    it('POST /bookings/10/cancel 호출 시 취소된 예약을 반환한다', async () => {
      const cancelled: BookingResponse = { ...mockBooking, status: 'CANCELLED' };
      mock.onPost('/bookings/10/cancel').reply(200, cancelled);

      const res = await client.post<BookingResponse>('/bookings/10/cancel', {
        reason: '일정 변경',
      });

      expect(res.data.status).toBe('CANCELLED');
      expect(res.data.id).toBe(10);
    });
  });

  describe('U-03: 없는 예약 취소', () => {
    it('POST /bookings/999/cancel 호출 시 404 에러가 발생한다', async () => {
      mock.onPost('/bookings/999/cancel').reply(404, { message: 'Not found' });

      await expect(client.post('/bookings/999/cancel', {})).rejects.toThrow();
    });
  });
});
