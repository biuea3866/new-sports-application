/**
 * usePurchaseLimitedDrop
 * - 202(ADMITTED) 결과를 admitted phase로 매핑하고 회차 쿼리를 무효화한다.
 * - 409(SOLD_OUT/CLOSED)·403(LIMIT_EXCEEDED)·425(TOO_EARLY) 결과를 각 phase로 매핑한다.
 * - 429(THROTTLED) 응답은 동일 Idempotency-Key로 1회 자동 재시도한다.
 * - 재시도로도 해소되지 않는 오류(네트워크·5xx)는 error phase로 매핑한다.
 * - 서로 다른 구매 시도(재시도가 아닌 신규 mutate)는 각각 새 Idempotency-Key를 생성한다.
 * - error phase 이후 재시도는 동일한 Idempotency-Key를 재사용해 중복 주문을 막는다.
 * - entryTokenStore에 저장된 입장 토큰이 있으면 구매 요청에 entryToken을 전달한다(FE-08).
 * - 토큰이 없으면 entryToken 없이 기존과 동일하게 호출한다(FE-08).
 * - 403 BYPASS_DENIED 결과를 bypassDenied phase로 매핑한다(FE-08).
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { usePurchaseLimitedDrop } from '../usePurchaseLimitedDrop';
import { useEntryTokenStore } from '../entryTokenStore';
import type { LimitedDropPurchaseResult } from '../../api/types';

jest.mock('../../api/limitedDrops', () => ({
  purchaseLimitedDrop: jest.fn(),
}));

jest.mock('../../api/goods', () => ({
  useCurrentUserId: jest.fn(() => 7),
}));

import { purchaseLimitedDrop } from '../../api/limitedDrops';

const purchaseLimitedDropMock = purchaseLimitedDrop as jest.MockedFunction<
  typeof purchaseLimitedDrop
>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

function extractIdempotencyKey(callIndex: number): string {
  const call = purchaseLimitedDropMock.mock.calls[callIndex];
  return call[2].idempotencyKey;
}

describe('usePurchaseLimitedDrop', () => {
  beforeEach(() => {
    useEntryTokenStore.setState({ tokens: {} });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('202 응답을 admitted phase로 매핑하고 회차 쿼리를 무효화한다', async () => {
    const admittedResult: LimitedDropPurchaseResult = {
      outcome: 'ADMITTED',
      data: { orderId: 42, dropId: 1, status: 'PENDING' },
    };
    purchaseLimitedDropMock.mockResolvedValue(admittedResult);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    await waitFor(() => expect(result.current.data?.phase).toBe('admitted'));
    if (result.current.data?.phase === 'admitted') {
      expect(result.current.data.data.orderId).toBe(42);
    }
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['limitedDrops', 1] });
  });

  it('409 SoldOut을 soldOut, 403을 limit, 425를 tooEarly로 매핑한다', async () => {
    const { wrapper } = createWrapper();

    purchaseLimitedDropMock.mockResolvedValueOnce({ outcome: 'SOLD_OUT' });
    const soldOutHook = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });
    await act(async () => {
      await soldOutHook.result.current.mutateAsync({ quantity: 1 });
    });
    await waitFor(() => expect(soldOutHook.result.current.data).toEqual({ phase: 'soldOut' }));

    purchaseLimitedDropMock.mockResolvedValueOnce({ outcome: 'LIMIT_EXCEEDED' });
    const limitHook = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });
    await act(async () => {
      await limitHook.result.current.mutateAsync({ quantity: 1 });
    });
    await waitFor(() => expect(limitHook.result.current.data).toEqual({ phase: 'limit' }));

    purchaseLimitedDropMock.mockResolvedValueOnce({
      outcome: 'TOO_EARLY',
      openAt: '2026-07-03T10:00:00Z',
    });
    const tooEarlyHook = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });
    await act(async () => {
      await tooEarlyHook.result.current.mutateAsync({ quantity: 1 });
    });
    await waitFor(() =>
      expect(tooEarlyHook.result.current.data).toEqual({
        phase: 'tooEarly',
        openAt: '2026-07-03T10:00:00Z',
      })
    );
  });

  it('429 응답을 동일한 Idempotency-Key로 1회 자동 재시도한다', async () => {
    purchaseLimitedDropMock.mockResolvedValueOnce({ outcome: 'THROTTLED' }).mockResolvedValueOnce({
      outcome: 'ADMITTED',
      data: { orderId: 99, dropId: 1, status: 'PENDING' },
    });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    expect(purchaseLimitedDropMock).toHaveBeenCalledTimes(2);
    expect(extractIdempotencyKey(0)).toBe(extractIdempotencyKey(1));
    await waitFor(() =>
      expect(result.current.data).toEqual({
        phase: 'admitted',
        data: { orderId: 99, dropId: 1, status: 'PENDING' },
      })
    );
  });

  it('재시도 후에도 THROTTLED이면 throttled phase를 반환한다', async () => {
    purchaseLimitedDropMock.mockResolvedValue({ outcome: 'THROTTLED' });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    expect(purchaseLimitedDropMock).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(result.current.data).toEqual({ phase: 'throttled' }));
  });

  it('5xx·네트워크 오류를 error phase로 매핑한다', async () => {
    purchaseLimitedDropMock.mockRejectedValue(new Error('Internal Server Error'));
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    await waitFor(() => expect(result.current.data).toEqual({ phase: 'error' }));
  });

  it('error phase 이후 재시도가 동일한 Idempotency-Key를 재사용한다', async () => {
    purchaseLimitedDropMock
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockResolvedValueOnce({
        outcome: 'ADMITTED',
        data: { orderId: 7, dropId: 1, status: 'PENDING' },
      });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });
    await waitFor(() => expect(result.current.data).toEqual({ phase: 'error' }));

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    expect(purchaseLimitedDropMock).toHaveBeenCalledTimes(2);
    expect(extractIdempotencyKey(0)).toBe(extractIdempotencyKey(1));
    await waitFor(() =>
      expect(result.current.data).toEqual({
        phase: 'admitted',
        data: { orderId: 7, dropId: 1, status: 'PENDING' },
      })
    );
  });

  it('신규 구매 시도마다 새 Idempotency-Key를 생성한다', async () => {
    purchaseLimitedDropMock.mockResolvedValue({
      outcome: 'ADMITTED',
      data: { orderId: 1, dropId: 1, status: 'PENDING' },
    });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });
    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    expect(purchaseLimitedDropMock).toHaveBeenCalledTimes(2);
    expect(extractIdempotencyKey(0)).not.toBe(extractIdempotencyKey(1));
  });

  it('저장된 입장 토큰이 있으면 구매 요청에 entryToken을 전달한다', async () => {
    useEntryTokenStore
      .getState()
      .setToken('limited-drop', 1, 'entry-token-xyz', new Date(Date.now() + 60_000).toISOString());
    purchaseLimitedDropMock.mockResolvedValue({
      outcome: 'ADMITTED',
      data: { orderId: 1, dropId: 1, status: 'PENDING' },
    });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    const call = purchaseLimitedDropMock.mock.calls[0];
    expect(call[2].entryToken).toBe('entry-token-xyz');
  });

  it('토큰이 없으면 entryToken 없이 기존과 동일하게 호출한다', async () => {
    purchaseLimitedDropMock.mockResolvedValue({
      outcome: 'ADMITTED',
      data: { orderId: 1, dropId: 1, status: 'PENDING' },
    });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    const call = purchaseLimitedDropMock.mock.calls[0];
    expect(call[2].entryToken).toBeUndefined();
  });

  it('403 BYPASS_DENIED 결과를 bypassDenied phase로 매핑한다', async () => {
    purchaseLimitedDropMock.mockResolvedValueOnce({ outcome: 'BYPASS_DENIED' });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePurchaseLimitedDrop(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ quantity: 1 });
    });

    await waitFor(() => expect(result.current.data).toEqual({ phase: 'bypassDenied' }));
  });
});
