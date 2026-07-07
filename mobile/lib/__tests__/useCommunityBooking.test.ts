/**
 * useCommunityBookings가 communityId로 연결 예약 목록을 반환한다
 * useCommunityBookings는 communityId가 0 이하면 쿼리를 실행하지 않는다
 * 연결된 예약이 없으면 빈 배열을 반환한다(정상)
 * useLinkCommunityBooking 성공 시 목록 캐시가 무효화된다
 * 비방장이 연결을 시도하면(403) 에러 상태로 전파된다
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import {
  communityBookingsQueryKey,
  useCommunityBookings,
  useLinkCommunityBooking,
} from '../useCommunityBooking';
import type {
  CommunityBookingListItemResponse,
  CommunityBookingResponse,
} from '../../api/community-types';

jest.mock('../../api/communityBooking', () => ({
  listCommunityBookings: jest.fn(),
  linkCommunityBooking: jest.fn(),
}));

import { linkCommunityBooking, listCommunityBookings } from '../../api/communityBooking';

const listCommunityBookingsMock = listCommunityBookings as jest.MockedFunction<
  typeof listCommunityBookings
>;
const linkCommunityBookingMock = linkCommunityBooking as jest.MockedFunction<
  typeof linkCommunityBooking
>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

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

describe('useCommunityBookings', () => {
  afterEach(() => jest.clearAllMocks());

  it('communityId로 연결 예약 목록을 반환한다', async () => {
    listCommunityBookingsMock.mockResolvedValue([mockBookingItem]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityBookings(5), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([mockBookingItem]);
    expect(listCommunityBookingsMock).toHaveBeenCalledWith(5);
  });

  it('communityId가 0 이하면 쿼리를 실행하지 않는다', () => {
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityBookings(0), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(listCommunityBookingsMock).not.toHaveBeenCalled();
  });

  it('연결된 예약이 없으면 빈 배열을 반환한다(정상)', async () => {
    listCommunityBookingsMock.mockResolvedValue([]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityBookings(6), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });
});

describe('useLinkCommunityBooking', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 연결 예약 목록 캐시가 무효화된다', async () => {
    const created: CommunityBookingResponse = {
      id: 1,
      communityId: 5,
      slotId: 20,
      linkedByUserId: 10,
      createdAt: '2026-07-08T00:00:00Z',
    };
    linkCommunityBookingMock.mockResolvedValue(created);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useLinkCommunityBooking(5), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ slotId: 20 });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(linkCommunityBookingMock).toHaveBeenCalledWith(5, 20);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: communityBookingsQueryKey(5) });
  });

  it('비방장이 연결을 시도하면(403) 에러 상태로 전파된다', async () => {
    linkCommunityBookingMock.mockRejectedValue(
      Object.assign(new Error('Forbidden'), { status: 403 })
    );
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useLinkCommunityBooking(5), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync({ slotId: 20 })).rejects.toThrow('Forbidden');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
