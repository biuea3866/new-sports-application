/**
 * U-01: createPayment는 Idempotency-Key 헤더와 함께 POST /payments를 호출하고 PaymentResponse를 반환한다
 * U-02: createPayment 호출 시 BE가 오류를 반환하면 예외가 전파된다
 * U-03: getMyPayments는 GET /payments/me를 호출하고 PaymentHistoryListResponse를 반환한다
 * U-04: getMyPayments에 status 필터를 전달하면 쿼리 파라미터로 포함된다
 * U-05: getMyPayments BE 500 응답 시 예외가 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { createPayment, getMyPayments, CreatePaymentBody, PaymentResponse } from '../payment';
import type { PaymentHistoryListResponse } from '../types';

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

const mockHistoryResponse: PaymentHistoryListResponse = {
  content: [
    {
      id: 10,
      orderType: 'BOOKING',
      orderId: 5,
      method: 'TOSS',
      provider: 'toss-payments',
      pgTransactionId: 'pg-txn-001',
      amount: 15000,
      currency: 'KRW',
      status: 'COMPLETED',
      paidAt: '2026-05-29T09:00:00Z',
      createdAt: '2026-05-29T08:59:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
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

describe('getMyPayments', () => {
  it('[U-03] GET /payments/me를 호출하고 PaymentHistoryListResponse를 반환한다', async () => {
    mock.onGet('/payments/me').reply(200, mockHistoryResponse);

    const result = await getMyPayments(0, 20);

    expect(result.content).toHaveLength(1);
    expect(result.content[0].id).toBe(10);
    expect(result.content[0].status).toBe('COMPLETED');
    expect(result.totalElements).toBe(1);
  });

  it('[U-04] status 필터를 전달하면 쿼리 파라미터에 포함된다', async () => {
    mock.onGet('/payments/me').reply(200, { ...mockHistoryResponse, content: [] });

    await getMyPayments(0, 20, 'COMPLETED');

    const requestHistory = mock.history.get;
    expect(requestHistory.length).toBe(1);
    expect(requestHistory[0].params).toMatchObject({ page: 0, size: 20, status: 'COMPLETED' });
  });

  it('[U-05] BE가 500을 반환하면 예외가 전파된다', async () => {
    mock.onGet('/payments/me').reply(500, { message: 'Internal Server Error' });

    await expect(getMyPayments()).rejects.toThrow();
  });
});
