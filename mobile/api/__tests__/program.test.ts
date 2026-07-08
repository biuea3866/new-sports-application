/**
 * U-01: listPrograms는 GET /facilities/{facilityId}/programs로 시설상품 목록을 반환한다
 * U-02: 등록된 상품이 없으면 빈 배열을 반환한다
 * U-03: facility.program.enabled 플래그가 꺼져있으면(404) 예외로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { listPrograms } from '../program';
import type { ProgramResponse } from '../program';

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

afterEach(() => mock.reset());

const mockProgram: ProgramResponse = {
  id: 1,
  facilityId: 'facility-1',
  ownerUserId: 42,
  name: 'PT 1:1',
  description: '개인 트레이닝',
  price: 50000,
  capacity: 1,
  durationMinutes: 60,
};

describe('U-01: listPrograms', () => {
  it('GET /facilities/facility-1/programs 호출 시 시설상품 목록을 반환한다', async () => {
    mock.onGet('/facilities/facility-1/programs').reply(200, [mockProgram]);

    const res = await listPrograms('facility-1');

    expect(res).toHaveLength(1);
    expect(res[0].name).toBe('PT 1:1');
    expect(res[0].price).toBe(50000);
  });
});

describe('U-02: 등록된 상품 없음', () => {
  it('빈 배열을 반환한다', async () => {
    mock.onGet('/facilities/facility-2/programs').reply(200, []);

    const res = await listPrograms('facility-2');

    expect(res).toHaveLength(0);
  });
});

describe('U-03: facility.program.enabled 플래그 OFF', () => {
  it('빈 자체가 등록되지 않아 404가 발생하면 예외로 전파된다', async () => {
    mock.onGet('/facilities/facility-3/programs').reply(404);

    await expect(listPrograms('facility-3')).rejects.toThrow();
  });
});
