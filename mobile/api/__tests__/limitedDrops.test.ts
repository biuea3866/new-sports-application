/**
 * U-01: getLimitedDrop이 200 응답을 LimitedDropResponse로 반환한다
 * U-02: purchaseLimitedDrop이 X-User-Id·Idempotency-Key 헤더와 {quantity} body를 보낸다
 * U-03: purchaseLimitedDrop이 202 응답을 ADMITTED outcome으로 파싱한다
 * U-04: purchaseLimitedDrop이 409/429/403/425 응답 상태코드를 판별 가능한 결과로 매핑한다
 * U-05: getLimitedDrop이 404 응답 시 에러를 던진다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { LimitedDropResponse, LimitedDropPurchaseResponse } from '../types';

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
import { getLimitedDrop, purchaseLimitedDrop } from '../limitedDrops';

const testInstance = (
  beClientModule as unknown as { _testInstance: ReturnType<typeof createBeClient> }
)._testInstance;
const mock = new MockAdapter(testInstance);

afterEach(() => {
  mock.reset();
});

afterAll(() => {
  mock.restore();
});

const mockDropResponse: LimitedDropResponse = {
  dropId: 1,
  productId: 100,
  status: 'OPEN',
  openAt: '2026-07-03T10:00:00Z',
  closeAt: '2026-07-03T12:00:00Z',
  remaining: 5,
  perUserLimit: 2,
  totalQuantity: 100,
  price: 89000,
};

const mockPurchaseResponse: LimitedDropPurchaseResponse = {
  orderId: 42,
  dropId: 1,
  status: 'PENDING',
};

describe('getLimitedDrop', () => {
  it('[U-01] GET /limited-drops/{dropId} 200 응답을 LimitedDropResponse로 반환한다', async () => {
    mock.onGet('/limited-drops/1').reply(200, mockDropResponse);

    const result = await getLimitedDrop(1);

    expect(result.dropId).toBe(1);
    expect(result.status).toBe('OPEN');
    expect(result.remaining).toBe(5);
    expect(result.perUserLimit).toBe(2);
  });

  it('[U-05] 404 응답 시 에러를 던진다', async () => {
    mock.onGet('/limited-drops/999').reply(404, { message: 'Not Found' });

    await expect(getLimitedDrop(999)).rejects.toThrow();
  });
});

describe('purchaseLimitedDrop', () => {
  it('[U-02] X-User-Id·Idempotency-Key 헤더와 {quantity} body를 전송한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(202, mockPurchaseResponse);

    await purchaseLimitedDrop(1, { quantity: 1 }, { userId: 7, idempotencyKey: 'idem-key-001' });

    const requestHistory = mock.history.post;
    expect(requestHistory.length).toBe(1);
    expect(requestHistory[0].url).toBe('/limited-drops/1/orders');
    expect(requestHistory[0].headers?.['X-User-Id']).toBe('7');
    expect(requestHistory[0].headers?.['Idempotency-Key']).toBe('idem-key-001');
    expect(JSON.parse(requestHistory[0].data as string)).toMatchObject({ quantity: 1 });
  });

  it('[U-03] 202 응답을 ADMITTED outcome의 LimitedDropPurchaseResponse로 파싱한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(202, mockPurchaseResponse);

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-002' }
    );

    expect(result.outcome).toBe('ADMITTED');
    if (result.outcome === 'ADMITTED') {
      expect(result.data.orderId).toBe(42);
      expect(result.data.status).toBe('PENDING');
    }
  });

  it('[U-04] 425 응답을 openAt을 포함한 TOO_EARLY outcome으로 매핑한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(425, { openAt: '2026-07-03T10:00:00Z' });

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-003' }
    );

    expect(result).toEqual({ outcome: 'TOO_EARLY', openAt: '2026-07-03T10:00:00Z' });
  });

  it('[U-04] 409 응답(code 없음)을 SOLD_OUT outcome으로 매핑한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(409, { message: 'Sold out' });

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-004' }
    );

    expect(result).toEqual({ outcome: 'SOLD_OUT' });
  });

  it('[U-04] 409 응답(code=LIMITED_DROP_CLOSED, 실제 BE 에러 코드)을 CLOSED outcome으로 매핑한다', async () => {
    mock
      .onPost('/limited-drops/1/orders')
      .reply(409, { code: 'LIMITED_DROP_CLOSED', message: 'Closed' });

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-005' }
    );

    expect(result).toEqual({ outcome: 'CLOSED' });
  });

  it('[U-04] 409 응답(code=LIMITED_DROP_SOLD_OUT, 실제 BE 에러 코드)을 SOLD_OUT outcome으로 매핑한다', async () => {
    mock
      .onPost('/limited-drops/1/orders')
      .reply(409, { code: 'LIMITED_DROP_SOLD_OUT', message: 'Sold out' });

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-005b' }
    );

    expect(result).toEqual({ outcome: 'SOLD_OUT' });
  });

  it('[U-04] 429 응답을 THROTTLED outcome으로 매핑한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(429, { message: 'Throttled' });

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-006' }
    );

    expect(result).toEqual({ outcome: 'THROTTLED' });
  });

  it('[U-04] 403 응답을 LIMIT_EXCEEDED outcome으로 매핑한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(403, { message: 'Per user limit exceeded' });

    const result = await purchaseLimitedDrop(
      1,
      { quantity: 1 },
      { userId: 7, idempotencyKey: 'idem-key-007' }
    );

    expect(result).toEqual({ outcome: 'LIMIT_EXCEEDED' });
  });

  it('[U-04] 5xx 응답은 판별 결과로 매핑하지 않고 에러를 전파한다', async () => {
    mock.onPost('/limited-drops/1/orders').reply(500, { message: 'Internal Server Error' });

    await expect(
      purchaseLimitedDrop(1, { quantity: 1 }, { userId: 7, idempotencyKey: 'idem-key-008' })
    ).rejects.toThrow();
  });
});
