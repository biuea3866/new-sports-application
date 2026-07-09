/**
 * FE-01: 가상 대기열 API 클라이언트 단위 테스트
 *
 * BE API 계약(TDD "FE/외부 계약 — API 명세"):
 * - POST /virtual-queues/{type}/{targetId}/entries → 200 QueueEntryResponse | 429 QUEUE_FULL
 * - GET  /virtual-queues/{type}/{targetId}/entries/me → 200 QueueEntryResponse | 404 NOT_IN_QUEUE
 * - DELETE /virtual-queues/{type}/{targetId}/entries/me → 204 (best-effort)
 *
 * 429/404는 정상 실패 경로이므로 throw하지 않고 판별 유니온으로 반환한다.
 * 5xx·네트워크 오류는 그대로 throw한다 (limitedDrops.ts#mapPurchaseFailure 선례와 동형).
 */
import MockAdapter from 'axios-mock-adapter';

import { createBeClient } from '../be-client';
import type { QueueEntryResponse } from '../virtualQueue';

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
import { enterQueue, getQueueStatus, leaveQueue } from '../virtualQueue';

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

const waitingResponse: QueueEntryResponse = {
  status: 'WAITING',
  position: 42,
  aheadCount: 41,
  etaSeconds: 120,
  entryToken: null,
  tokenExpiresAt: null,
};

const admittedResponse: QueueEntryResponse = {
  status: 'ADMITTED',
  position: null,
  aheadCount: null,
  etaSeconds: null,
  entryToken: 'entry-token-abc',
  tokenExpiresAt: '2026-07-09T10:05:00Z',
};

describe('enterQueue', () => {
  it('200 응답을 QueueEntryResponse로 매핑해 ENTERED로 반환한다', async () => {
    mock.onPost('/virtual-queues/limited-drop/1/entries').reply(200, admittedResponse);

    const result = await enterQueue('limited-drop', 1, 7);

    expect(result).toEqual({ outcome: 'ENTERED', data: admittedResponse });
  });

  it('429 응답을 throw 없이 FULL 판별로 반환한다', async () => {
    mock
      .onPost('/virtual-queues/limited-drop/1/entries')
      .reply(429, { code: 'QUEUE_FULL', detail: '잠시 후 다시 시도' });

    const result = await enterQueue('limited-drop', 1, 7);

    expect(result).toEqual({ outcome: 'FULL' });
  });

  it('5xx 오류는 판별 유니온으로 흡수하지 않고 그대로 throw한다', async () => {
    mock.onPost('/virtual-queues/limited-drop/1/entries').reply(500);

    await expect(enterQueue('limited-drop', 1, 7)).rejects.toThrow();
  });

  it('네트워크 오류는 그대로 throw한다', async () => {
    mock.onPost('/virtual-queues/limited-drop/1/entries').networkError();

    await expect(enterQueue('limited-drop', 1, 7)).rejects.toThrow();
  });

  it('X-User-Id 헤더가 부착되고 body 없이 요청한다', async () => {
    mock.onPost('/virtual-queues/ticketing-event/9/entries').reply(200, admittedResponse);

    await enterQueue('ticketing-event', 9, 123);

    const requestHistory = mock.history.post;
    expect(requestHistory.length).toBe(1);
    expect(requestHistory[0].headers?.['X-User-Id']).toBe('123');
    expect(requestHistory[0].data).toBeUndefined();
  });
});

describe('getQueueStatus', () => {
  it('200 WAITING 응답의 position/aheadCount/etaSeconds를 number로 좁혀 OK 판별로 반환한다', async () => {
    mock.onGet('/virtual-queues/limited-drop/1/entries/me').reply(200, waitingResponse);

    const result = await getQueueStatus('limited-drop', 1, 7);

    expect(result.outcome).toBe('OK');
    if (result.outcome === 'OK') {
      expect(typeof result.data.position).toBe('number');
      expect(typeof result.data.aheadCount).toBe('number');
      expect(typeof result.data.etaSeconds).toBe('number');
      expect(result.data.position).toBe(42);
    }
  });

  it('404 응답을 NOT_IN_QUEUE 판별로 반환한다', async () => {
    mock.onGet('/virtual-queues/limited-drop/1/entries/me').reply(404);

    const result = await getQueueStatus('limited-drop', 1, 7);

    expect(result).toEqual({ outcome: 'NOT_IN_QUEUE' });
  });

  it('5xx 오류는 판별 유니온으로 흡수하지 않고 그대로 throw한다', async () => {
    mock.onGet('/virtual-queues/limited-drop/1/entries/me').reply(503);

    await expect(getQueueStatus('limited-drop', 1, 7)).rejects.toThrow();
  });

  it('X-User-Id 헤더가 부착된다', async () => {
    mock.onGet('/virtual-queues/limited-drop/1/entries/me').reply(200, waitingResponse);

    await getQueueStatus('limited-drop', 1, 55);

    const requestHistory = mock.history.get;
    expect(requestHistory[0].headers?.['X-User-Id']).toBe('55');
  });
});

describe('leaveQueue', () => {
  it('204 응답이면 정상 완료된다', async () => {
    mock.onDelete('/virtual-queues/limited-drop/1/entries/me').reply(204);

    await expect(leaveQueue('limited-drop', 1, 7)).resolves.toBeUndefined();
  });

  it('X-User-Id 헤더가 부착된다', async () => {
    mock.onDelete('/virtual-queues/limited-drop/1/entries/me').reply(204);

    await leaveQueue('limited-drop', 1, 33);

    const requestHistory = mock.history.delete;
    expect(requestHistory[0].headers?.['X-User-Id']).toBe('33');
  });

  it('실패 시 호출부가 처리할 수 있도록 그대로 throw한다', async () => {
    mock.onDelete('/virtual-queues/limited-drop/1/entries/me').reply(500);

    await expect(leaveQueue('limited-drop', 1, 7)).rejects.toThrow();
  });
});
