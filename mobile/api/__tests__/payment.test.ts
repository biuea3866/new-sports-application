/**
 * U-01: createPayment는 Idempotency-Key 헤더와 함께 POST /payments를 호출하고 PaymentResponse를 반환한다
 * U-02: createPayment 호출 시 BE가 오류를 반환하면 예외가 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { createPayment, CreatePaymentBody, PaymentResponse } from '../payment';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => ({
  router: { replace: jest.fn() },
}));

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  const instance = actual.createBeClient('http://localhost:8080');
  return {
    ...actual,
    getBeClient: jest.fn(() => instance),
    _testInstance: instance,
  };
});

import * as beClientModule from '../be-client';

const testInstance = (beClientModule as unknown as { _testInstance: ReturnType<typeof createBeClient> })._testInstance;
const mock = new MockAdapter(testInstance);

const mockBody: CreatePaymentBody = {
  orderType: 'BOOKING',
  orderId: 42,
  method: 'KAKAO',
  amount: 30000,
  currency: 'KRW',
};

const mockResponse: PaymentResponse = {
  id: 1,
  orderType: 'BOOKING',
  orderId: 42,
  method: 'KAKAO',
  amount: 30000,
  status: 'COMPLETED',
  createdAt: '2026-05-30T10:00:00Z',
  paidAt: '2026-05-30T10:00:01Z',
};

afterEach(() => {
  mock.reset();
});

afterAll(() => {
  mock.restore();
});

describe('createPayment', () => {
  it('[U-01] Idempotency-Key 헤더와 함께 POST /payments를 호출하고 PaymentResponse를 반환한다', async () => {
    mock.onPost('/payments').reply(200, mockResponse);

    const result = await createPayment(mockBody, 'test-uuid-1234');

    expect(result.status).toBe('COMPLETED');
    expect(result.id).toBe(1);
    expect(result.orderId).toBe(42);

    const requestHistory = mock.history.post;
    expect(requestHistory.length).toBe(1);
    expect(requestHistory[0].headers?.['Idempotency-Key']).toBe('test-uuid-1234');
    expect(JSON.parse(requestHistory[0].data as string)).toMatchObject({
      orderType: 'BOOKING',
      orderId: 42,
      method: 'KAKAO',
      amount: 30000,
      currency: 'KRW',
    });
  });

  it('[U-02] BE가 500을 반환하면 예외가 전파된다', async () => {
    mock.onPost('/payments').reply(500, { message: 'Internal Server Error' });

    await expect(createPayment(mockBody, 'test-uuid-5678')).rejects.toThrow();
  });
});
