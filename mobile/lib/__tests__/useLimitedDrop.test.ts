/**
 * U-01: useLimitedDrop이 200 응답을 LimitedDropResponse로 반환한다
 * U-02: status가 OPEN이거나 openAt이 임박했을 때 refetchInterval이 활성화된다
 * U-03: 404 응답 시 error 상태를 노출한다
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { getRefetchIntervalMs, useLimitedDrop } from '../useLimitedDrop';
import type { LimitedDropResponse } from '../../api/types';

jest.mock('../../api/limitedDrops', () => ({
  getLimitedDrop: jest.fn(),
}));

import { getLimitedDrop } from '../../api/limitedDrops';

const getLimitedDropMock = getLimitedDrop as jest.MockedFunction<typeof getLimitedDrop>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const openDrop: LimitedDropResponse = {
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

const scheduledFarDrop: LimitedDropResponse = {
  ...openDrop,
  status: 'SCHEDULED',
  openAt: '2099-01-01T00:00:00Z',
};

describe('useLimitedDrop', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('[U-01] 200 응답을 LimitedDropResponse로 반환한다', async () => {
    getLimitedDropMock.mockResolvedValue(openDrop);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useLimitedDrop(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(openDrop);
    expect(getLimitedDropMock).toHaveBeenCalledWith(1);

    // status가 OPEN이면 refetchInterval이 실 타이머를 예약하므로 반드시 정리한다
    unmount();
    queryClient.clear();
  });

  it('[U-03] 404 응답 시 error 상태를 노출한다', async () => {
    getLimitedDropMock.mockRejectedValue(new Error('Not Found'));
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useLimitedDrop(999), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error?.message).toBe('Not Found');

    unmount();
    queryClient.clear();
  });
});

describe('getRefetchIntervalMs', () => {
  it('[U-02] status가 OPEN이면 폴링 간격을 반환한다', () => {
    expect(getRefetchIntervalMs(openDrop)).toBe(3000);
  });

  it('[U-02] openAt이 임박(1분 이내)하면 폴링 간격을 반환한다', () => {
    const nearOpenDrop: LimitedDropResponse = {
      ...openDrop,
      status: 'SCHEDULED',
      openAt: new Date(Date.now() + 30 * 1000).toISOString(),
    };

    expect(getRefetchIntervalMs(nearOpenDrop)).toBe(3000);
  });

  it('[U-02] openAt이 먼 미래이면 폴링하지 않는다', () => {
    expect(getRefetchIntervalMs(scheduledFarDrop)).toBe(false);
  });

  it('[U-02] 데이터가 없으면 폴링하지 않는다', () => {
    expect(getRefetchIntervalMs(undefined)).toBe(false);
  });
});
