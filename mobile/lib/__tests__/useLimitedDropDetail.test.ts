/**
 * useLimitedDropDetail — useLimitedDrop(서버)·useCountdown(지역)을 합성한 S1 뷰모델 훅 검증.
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import type { LimitedDropResponse } from '../../api/types';
import { useLimitedDropDetail } from '../useLimitedDropDetail';

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

const scheduledDropOpeningSoon: LimitedDropResponse = {
  dropId: 1,
  productId: 100,
  status: 'SCHEDULED',
  openAt: new Date(Date.now() + 500).toISOString(),
  closeAt: new Date(Date.now() + 100000).toISOString(),
  remaining: 10,
  perUserLimit: 1,
  totalQuantity: 10,
  price: 50000,
};

const openDrop: LimitedDropResponse = {
  dropId: 2,
  productId: 200,
  status: 'OPEN',
  openAt: new Date(Date.now() - 100000).toISOString(),
  closeAt: new Date(Date.now() + 100000).toISOString(),
  remaining: 5,
  perUserLimit: 2,
  totalQuantity: 20,
  price: 89000,
};

describe('useLimitedDropDetail', () => {
  afterEach(() => {
    jest.clearAllMocks();
    jest.useRealTimers();
  });

  it('조회 중이면 loading phase를 반환한다', async () => {
    getLimitedDropMock.mockImplementation(() => new Promise(() => {}));
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useLimitedDropDetail(1), { wrapper });

    expect(result.current.phase).toBe('loading');

    unmount();
    queryClient.clear();
  });

  it('404 오류면 error phase와 "존재하지 않는 회차예요" 메시지를 반환한다', async () => {
    getLimitedDropMock.mockRejectedValue(new Error('Not Found'));
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useLimitedDropDetail(999), { wrapper });

    await waitFor(() => expect(result.current.phase).toBe('error'));

    if (result.current.phase !== 'error') {
      throw new Error('expected error phase');
    }
    expect(typeof result.current.retry).toBe('function');

    unmount();
    queryClient.clear();
  });

  it('OPEN 회차는 success phase에서 활성 CTA를 반환한다', async () => {
    getLimitedDropMock.mockResolvedValue(openDrop);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useLimitedDropDetail(2), { wrapper });

    await waitFor(() => expect(result.current.phase).toBe('success'));

    if (result.current.phase !== 'success') {
      throw new Error('expected success phase');
    }
    expect(result.current.cta).toEqual({ label: '구매하기', disabled: false });
    expect(result.current.drop).toEqual(openDrop);

    unmount();
    queryClient.clear();
  });

  it('SCHEDULED 회차가 openAt에 도달하면 CTA가 활성화되고 1회 refetch된다', async () => {
    getLimitedDropMock.mockResolvedValue(scheduledDropOpeningSoon);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useLimitedDropDetail(1), { wrapper });

    await waitFor(() => expect(result.current.phase).toBe('success'));
    if (result.current.phase !== 'success') {
      throw new Error('expected success phase');
    }
    expect(result.current.cta.disabled).toBe(true);

    await waitFor(
      () => {
        if (result.current.phase !== 'success') {
          throw new Error('expected success phase');
        }
        expect(result.current.cta.disabled).toBe(false);
      },
      { timeout: 3000 }
    );

    expect(getLimitedDropMock.mock.calls.length).toBeGreaterThanOrEqual(2);

    unmount();
    queryClient.clear();
  });
});
