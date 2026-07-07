/**
 * U-01: listCommunityBookings는 GET /communities/{id}/bookings로 연결 예약 목록을 반환한다
 * U-02: 연결된 예약이 없으면 빈 배열을 반환한다(정상)
 * U-03: linkCommunityBooking은 POST /communities/{id}/bookings로 slotId를 전달한다
 * U-04: 비방장이 연결을 시도하면 403 에러가 발생한다
 * U-05: community.booking.enabled 플래그가 꺼져있으면(404) 예외로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { linkCommunityBooking, listCommunityBookings } from '../communityBooking';
import type {
  CommunityBookingListItemResponse,
  CommunityBookingResponse,
} from '../community-types';

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

const mockBookingItem: CommunityBookingListItemResponse = {
  id: 1,
  communityId: 5,
  slotId: 20,
  linkedByUserId: 10,
  facilityId: 'facility-1',
  date: '2026-07-12T14:00:00+09:00',
  timeRange: '14:00 - 15:00',
  capacity: 8,
};

describe('U-01: listCommunityBookings', () => {
  it('GET /communities/5/bookings 호출 시 연결 예약 목록을 반환한다', async () => {
    mock.onGet('/communities/5/bookings').reply(200, [mockBookingItem]);

    const res = await listCommunityBookings(5);

    expect(res).toHaveLength(1);
    expect(res[0].facilityId).toBe('facility-1');
  });
});

describe('U-02: 연결된 예약 없음', () => {
  it('빈 배열을 반환한다', async () => {
    mock.onGet('/communities/6/bookings').reply(200, []);

    const res = await listCommunityBookings(6);

    expect(res).toHaveLength(0);
  });
});

describe('U-03: linkCommunityBooking', () => {
  it('POST /communities/5/bookings 호출 시 slotId를 본문으로 전달하고 결과를 반환한다', async () => {
    const created: CommunityBookingResponse = {
      id: 1,
      communityId: 5,
      slotId: 20,
      linkedByUserId: 10,
      createdAt: '2026-07-08T00:00:00Z',
    };
    mock.onPost('/communities/5/bookings').reply(200, created);

    const res = await linkCommunityBooking(5, 20);

    expect(res.slotId).toBe(20);
    expect(JSON.parse(mock.history.post[0].data as string)).toEqual({ slotId: 20 });
  });

  it('U-04 비방장이 연결을 시도하면 403 에러가 발생한다', async () => {
    mock.onPost('/communities/5/bookings').reply(403, { message: 'Forbidden' });

    await expect(linkCommunityBooking(5, 20)).rejects.toThrow();
  });
});

describe('U-05: community.booking.enabled 플래그 OFF', () => {
  it('빈 자체가 등록되지 않아 404가 발생하면 예외로 전파된다', async () => {
    mock.onGet('/communities/7/bookings').reply(404);

    await expect(listCommunityBookings(7)).rejects.toThrow();
  });
});
