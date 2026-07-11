/**
 * useEnterQueue — 대기열 진입 mutation 훅 검증
 * 근거: 티켓 FE-05, design-fe-app.md "API 연동 표"(S1 진입) · "Testing Plan"
 *
 * - 진입 성공(ENTERED) 시 결과를 그대로 노출하고, 입장 토큰이 있으면 entryTokenStore에 저장한다.
 * - 429 포화 응답은 error가 아닌 FULL 판별 결과로 노출된다.
 * - 5xx·네트워크 오류는 mutation error 상태로 전파된다(화면이 error로 분기).
 * - 요청에 useCurrentUserId 값이 X-User-Id 인자로 전달된다.
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { useEnterQueue } from '../useEnterQueue';
import { useEntryTokenStore } from '../entryTokenStore';
import type { QueueEnterResult } from '../../api/virtualQueue';

jest.mock('../../api/virtualQueue', () => ({
  enterQueue: jest.fn(),
}));

jest.mock('../../api/goods', () => ({
  useCurrentUserId: jest.fn(() => 7),
}));

import { enterQueue } from '../../api/virtualQueue';

const enterQueueMock = enterQueue as jest.MockedFunction<typeof enterQueue>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return wrapper;
}

describe('useEnterQueue', () => {
  beforeEach(() => {
    useEntryTokenStore.setState({ tokens: {} });
    jest.clearAllMocks();
  });

  it('진입 성공(ADMITTED) 시 ENTERED 결과를 반환하고 입장 토큰을 저장한다', async () => {
    const entered: QueueEnterResult = {
      outcome: 'ENTERED',
      data: {
        status: 'ADMITTED',
        position: null,
        aheadCount: null,
        etaSeconds: null,
        entryToken: 'entry-token-abc',
        tokenExpiresAt: '2026-07-10T00:10:00Z',
      },
    };
    enterQueueMock.mockResolvedValue(entered);
    const wrapper = createWrapper();

    const { result } = renderHook(() => useEnterQueue('limited-drop', 42), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(result.current.data).toEqual(entered));
    expect(useEntryTokenStore.getState().tokenFor('limited-drop', 42)).toBe('entry-token-abc');
  });

  it('진입 성공(WAITING) 시 토큰이 없으므로 entryTokenStore에 저장하지 않는다', async () => {
    const waiting: QueueEnterResult = {
      outcome: 'ENTERED',
      data: {
        status: 'WAITING',
        position: 12,
        aheadCount: 11,
        etaSeconds: 55,
        entryToken: null,
        tokenExpiresAt: null,
      },
    };
    enterQueueMock.mockResolvedValue(waiting);
    const wrapper = createWrapper();

    const { result } = renderHook(() => useEnterQueue('limited-drop', 42), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(result.current.data).toEqual(waiting));
    expect(useEntryTokenStore.getState().tokenFor('limited-drop', 42)).toBeNull();
  });

  it('429 포화 응답은 error가 아닌 FULL 결과로 노출된다', async () => {
    enterQueueMock.mockResolvedValue({ outcome: 'FULL' });
    const wrapper = createWrapper();

    const { result } = renderHook(() => useEnterQueue('ticketing-event', 5), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(result.current.data).toEqual({ outcome: 'FULL' }));
    expect(result.current.isError).toBe(false);
  });

  it('5xx·네트워크 오류는 mutation error 상태로 전파된다', async () => {
    enterQueueMock.mockRejectedValue(new Error('Internal Server Error'));
    const wrapper = createWrapper();

    const { result } = renderHook(() => useEnterQueue('limited-drop', 42), { wrapper });

    await act(async () => {
      await result.current.mutateAsync().catch(() => undefined);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
  });

  it('요청에 useCurrentUserId 값이 userId 인자로 전달된다', async () => {
    enterQueueMock.mockResolvedValue({ outcome: 'FULL' });
    const wrapper = createWrapper();

    const { result } = renderHook(() => useEnterQueue('limited-drop', 42), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(enterQueueMock).toHaveBeenCalledWith('limited-drop', 42, 7);
  });
});
