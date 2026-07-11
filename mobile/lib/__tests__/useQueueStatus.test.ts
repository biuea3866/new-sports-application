/**
 * U-01: enabled true일 때 3초 간격으로 재조회한다
 * U-02: 404(NOT_IN_QUEUE) 응답을 결과 데이터로 그대로 노출한다
 * U-03: WAITING 응답의 position/aheadCount/etaSeconds를 그대로 반환한다
 * U-04: ADMITTED 응답을 그대로 노출한다(상위 뷰모델의 입장 감지 근거)
 * U-05: enabled false면 폴링하지 않는다
 * U-06: 5xx 3회 연속 실패 시 폴링을 중단한다
 * U-07: getQueueStatusRefetchIntervalMs — 연속 실패 횟수로 다음 폴링 간격을 결정한다
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react-native';

import { getQueueStatusRefetchIntervalMs, useQueueStatus } from '../useQueueStatus';
import type { QueueEntryResponse, QueueStatusResult } from '../../api/virtualQueue';

jest.mock('../../api/virtualQueue', () => ({
  getQueueStatus: jest.fn(),
}));

import { getQueueStatus } from '../../api/virtualQueue';

const getQueueStatusMock = getQueueStatus as jest.MockedFunction<typeof getQueueStatus>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const waitingEntry: QueueEntryResponse = {
  status: 'WAITING',
  position: 5,
  aheadCount: 4,
  etaSeconds: 120,
  entryToken: null,
  tokenExpiresAt: null,
};

const admittedEntry: QueueEntryResponse = {
  status: 'ADMITTED',
  position: null,
  aheadCount: null,
  etaSeconds: null,
  entryToken: 'entry-token-value',
  tokenExpiresAt: '2026-07-10T00:05:00.000Z',
};

describe('useQueueStatus', () => {
  afterEach(() => {
    jest.clearAllMocks();
    jest.useRealTimers();
  });

  it('enabled true일 때 3초 간격으로 재조회한다', async () => {
    jest.useFakeTimers();
    getQueueStatusMock.mockResolvedValue({ outcome: 'OK', data: waitingEntry });
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useQueueStatus('limited-drop', 1, 10, true), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getQueueStatusMock).toHaveBeenCalledTimes(1);

    await waitFor(() => expect(getQueueStatusMock).toHaveBeenCalledTimes(2), {
      timeout: 5000,
    });

    unmount();
    queryClient.clear();
  });

  it('404(NOT_IN_QUEUE) 응답을 결과 데이터로 그대로 노출한다', async () => {
    getQueueStatusMock.mockResolvedValue({ outcome: 'NOT_IN_QUEUE' });
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useQueueStatus('limited-drop', 1, 10, true), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const expected: QueueStatusResult = { outcome: 'NOT_IN_QUEUE' };
    expect(result.current.data).toEqual(expected);

    unmount();
    queryClient.clear();
  });

  it('WAITING 응답의 position/aheadCount/etaSeconds를 그대로 반환한다', async () => {
    getQueueStatusMock.mockResolvedValue({ outcome: 'OK', data: waitingEntry });
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useQueueStatus('limited-drop', 1, 10, true), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({ outcome: 'OK', data: waitingEntry });

    unmount();
    queryClient.clear();
  });

  it('ADMITTED 응답을 그대로 노출한다(상위 뷰모델의 입장 감지 근거)', async () => {
    getQueueStatusMock.mockResolvedValue({ outcome: 'OK', data: admittedEntry });
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useQueueStatus('limited-drop', 1, 10, true), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({ outcome: 'OK', data: admittedEntry });
    expect(result.current.data?.outcome === 'OK' && result.current.data.data.status).toBe(
      'ADMITTED'
    );

    unmount();
    queryClient.clear();
  });

  it('enabled false면 폴링하지 않는다', async () => {
    jest.useFakeTimers();
    getQueueStatusMock.mockResolvedValue({ outcome: 'OK', data: waitingEntry });
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useQueueStatus('limited-drop', 1, 10, false), {
      wrapper,
    });

    await act(async () => {
      jest.advanceTimersByTime(10_000);
    });

    expect(getQueueStatusMock).not.toHaveBeenCalled();
    expect(result.current.status).toBe('pending');
    expect(result.current.fetchStatus).toBe('idle');

    unmount();
    queryClient.clear();
  });

  it('5xx 3회 연속 실패 시 폴링을 중단한다', async () => {
    jest.useFakeTimers();
    getQueueStatusMock.mockRejectedValue(new Error('Internal Server Error'));
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useQueueStatus('limited-drop', 1, 10, true), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    await waitFor(() => expect(getQueueStatusMock).toHaveBeenCalledTimes(3), {
      timeout: 12_000,
    });

    // 연속 3회 실패 이후에는 refetchInterval이 false를 반환하므로 더 이상 예약된 재조회가 없다.
    await act(async () => {
      jest.advanceTimersByTime(60_000);
    });
    expect(getQueueStatusMock).toHaveBeenCalledTimes(3);

    unmount();
    queryClient.clear();
  });
});

describe('getQueueStatusRefetchIntervalMs', () => {
  it('연속 실패 횟수가 3 미만이면 3000ms를 반환한다', () => {
    expect(getQueueStatusRefetchIntervalMs(0)).toBe(3000);
    expect(getQueueStatusRefetchIntervalMs(1)).toBe(3000);
    expect(getQueueStatusRefetchIntervalMs(2)).toBe(3000);
  });

  it('연속 실패 횟수가 3 이상이면 false를 반환한다', () => {
    expect(getQueueStatusRefetchIntervalMs(3)).toBe(false);
    expect(getQueueStatusRefetchIntervalMs(4)).toBe(false);
  });
});
