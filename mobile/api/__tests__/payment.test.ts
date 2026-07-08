/**
 * U-01: createPayment는 Idempotency-Key 헤더와 함께 POST /payments를 호출하고 PaymentResponse를 반환한다
 * U-02: createPayment 호출 시 BE가 오류를 반환하면 예외가 전파된다
 * U-03: preparePayment는 Idempotency-Key 헤더와 함께 POST /payments/prepare를 호출하고 PreparePaymentResponse를 반환한다
 * U-04: preparePayment 호출 시 BE가 500을 반환하면 예외가 전파된다
 * U-05: getPayment는 GET /payments/{id}를 호출하고 PaymentDetailResponse를 반환한다
 * U-06: getPayment 호출 시 BE가 404를 반환하면 예외가 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import {
  createPayment,
  preparePayment,
  getPayment,
  isPreIssuedPaymentParams,
  CreatePaymentBody,
  PaymentResponse,
  PreparePaymentBody,
  PreparePaymentResponse,
  PaymentDetailResponse,
} from '../payment';

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

const testInstance = (
  beClientModule as unknown as { _testInstance: ReturnType<typeof createBeClient> }
)._testInstance;
const mock = new MockAdapter(testInstance);

const mockCreateBody: CreatePaymentBody = {
  orderType: 'BOOKING',
  orderId: 42,
  method: 'KAKAO',
  amount: 30000,
  currency: 'KRW',
};

const mockCreateResponse: PaymentResponse = {
  id: 1,
  orderType: 'BOOKING',
  orderId: 42,
  method: 'KAKAO',
  amount: 30000,
  status: 'COMPLETED',
  createdAt: '2026-05-30T10:00:00Z',
  paidAt: '2026-05-30T10:00:01Z',
};

const mockPrepareBody: PreparePaymentBody = {
  orderType: 'BOOKING',
  orderId: 42,
  method: 'TOSS',
  amount: 50000,
  currency: 'KRW',
  itemName: '테스트 상품',
  returnUrl: 'myapp://payment/result',
  failUrl: 'myapp://payment/result',
};

const mockPrepareResponse: PreparePaymentResponse = {
  paymentId: 99,
  checkoutUrl: 'https://mock-pg.example.com/checkout/abc123',
  pgTransactionId: 'pg-tx-abc123',
};

const mockDetailResponse: PaymentDetailResponse = {
  id: 99,
  orderType: 'BOOKING',
  orderId: 42,
  method: 'TOSS',
  amount: 50000,
  currency: 'KRW',
  status: 'COMPLETED',
  itemName: '테스트 상품',
  pgTransactionId: 'pg-tx-abc123',
  createdAt: '2026-05-30T10:00:00Z',
  paidAt: '2026-05-30T10:01:00Z',
};

afterEach(() => {
  mock.reset();
});

afterAll(() => {
  mock.restore();
});

describe('createPayment', () => {
  it('[U-01] Idempotency-Key 헤더와 함께 POST /payments를 호출하고 PaymentResponse를 반환한다', async () => {
    mock.onPost('/payments').reply(200, mockCreateResponse);

    const result = await createPayment(mockCreateBody, 'test-uuid-1234');

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

    await expect(createPayment(mockCreateBody, 'test-uuid-5678')).rejects.toThrow();
  });
});

describe('preparePayment', () => {
  it('[U-03] Idempotency-Key 헤더와 함께 POST /payments/prepare를 호출하고 PreparePaymentResponse를 반환한다', async () => {
    mock.onPost('/payments/prepare').reply(201, mockPrepareResponse);

    const result = await preparePayment(mockPrepareBody, 'idempotency-key-prepare');

    expect(result.paymentId).toBe(99);
    expect(result.checkoutUrl).toBe('https://mock-pg.example.com/checkout/abc123');
    expect(result.pgTransactionId).toBe('pg-tx-abc123');

    const requestHistory = mock.history.post;
    expect(requestHistory.length).toBe(1);
    expect(requestHistory[0].url).toBe('/payments/prepare');
    expect(requestHistory[0].headers?.['Idempotency-Key']).toBe('idempotency-key-prepare');
    expect(JSON.parse(requestHistory[0].data as string)).toMatchObject({
      orderType: 'BOOKING',
      orderId: 42,
      method: 'TOSS',
      amount: 50000,
      currency: 'KRW',
      itemName: '테스트 상품',
      returnUrl: 'myapp://payment/result',
      failUrl: 'myapp://payment/result',
    });
  });

  it('[U-04] BE가 500을 반환하면 예외가 전파된다', async () => {
    mock.onPost('/payments/prepare').reply(500, { message: 'Internal Server Error' });

    await expect(preparePayment(mockPrepareBody, 'idempotency-key-err')).rejects.toThrow();
  });
});

describe('getPayment', () => {
  it('[U-05] GET /payments/{id}를 호출하고 PaymentDetailResponse를 반환한다', async () => {
    mock.onGet('/payments/99').reply(200, mockDetailResponse);

    const result = await getPayment(99);

    expect(result.id).toBe(99);
    expect(result.status).toBe('COMPLETED');
    expect(result.method).toBe('TOSS');
    expect(result.pgTransactionId).toBe('pg-tx-abc123');
    expect(result.paidAt).toBe('2026-05-30T10:01:00Z');
  });

  it('[U-06] BE가 404를 반환하면 예외가 전파된다', async () => {
    mock.onGet('/payments/9999').reply(404, { message: 'Not Found' });

    await expect(getPayment(9999)).rejects.toThrow();
  });
});

describe('createPayment · OrderType RECRUITMENT', () => {
  it('[U-07] orderType RECRUITMENT로 POST /payments를 호출할 수 있다', async () => {
    const recruitmentBody: CreatePaymentBody = {
      orderType: 'RECRUITMENT',
      orderId: 7,
      method: 'TOSS',
      amount: 5000,
      currency: 'KRW',
    };
    mock
      .onPost('/payments')
      .reply(200, { ...mockCreateResponse, orderType: 'RECRUITMENT', orderId: 7 });

    const result = await createPayment(recruitmentBody, 'test-uuid-recruitment');

    expect(result.orderType).toBe('RECRUITMENT');
    expect(result.orderId).toBe(7);
  });
});

describe('isPreIssuedPaymentParams', () => {
  it('[U-08] paymentId·checkoutUrl이 모두 단일 문자열이면 true를 반환한다', () => {
    expect(
      isPreIssuedPaymentParams({ paymentId: '99', checkoutUrl: 'https://mock-pg.example.com/x' })
    ).toBe(true);
  });

  it('[U-09] paymentId·checkoutUrl이 없으면 false를 반환한다(일반 진입)', () => {
    expect(isPreIssuedPaymentParams({})).toBe(false);
  });

  it('[U-10] checkoutUrl이 문자열 배열이면 false를 반환한다(비정상 라우트 파라미터)', () => {
    expect(isPreIssuedPaymentParams({ paymentId: '99', checkoutUrl: ['a', 'b'] })).toBe(false);
  });
});
