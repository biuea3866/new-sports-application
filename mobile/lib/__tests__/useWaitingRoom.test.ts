/**
 * useWaitingRoom — 대기실 뷰모델 오케스트레이션(진입→폴링→phase 판별→자동전환) 검증.
 * 근거: 티켓 FE-07 "테스트 케이스" · design-fe-app.md "처리 흐름" · "상태관리 설계".
 *
 * useEnterQueue/useQueueStatus(FE-05/06)는 각자 티켓에서 이미 검증됐으므로 여기서는
 * 모킹해 두 훅의 응답을 useWaitingRoom이 어떻게 phase로 합성하는지만 검증한다.
 */
import { act } from 'react';
import { renderHook, waitFor } from '@testing-library/react-native';
import { useRouter } from 'expo-router';

import { useWaitingRoom } from '../useWaitingRoom';
import { useEntryTokenStore } from '../entryTokenStore';
import { ROUTES } from '../navigation';
import type { QueueEnterResult, QueueStatusResult, QueueTargetType } from '../../api/virtualQueue';

jest.mock('../useEnterQueue', () => ({ useEnterQueue: jest.fn() }));
jest.mock('../useQueueStatus', () => {
  const actual = jest.requireActual('../useQueueStatus');
  return { ...actual, useQueueStatus: jest.fn() };
});
jest.mock('../../api/goods', () => ({ useCurrentUserId: jest.fn(() => 7) }));
jest.mock('../../api/virtualQueue', () => {
  const actual = jest.requireActual('../../api/virtualQueue');
  return { ...actual, leaveQueue: jest.fn().mockResolvedValue(undefined) };
});

import { useEnterQueue } from '../useEnterQueue';
import { useQueueStatus } from '../useQueueStatus';
import { leaveQueue } from '../../api/virtualQueue';

const useEnterQueueMock = useEnterQueue as jest.MockedFunction<typeof useEnterQueue>;
const useQueueStatusMock = useQueueStatus as jest.MockedFunction<typeof useQueueStatus>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const leaveQueueMock = leaveQueue as jest.MockedFunction<typeof leaveQueue>;

function mockEnterQueueReturn(
  mutate: jest.Mock,
  overrides: Partial<{ data: QueueEnterResult; isError: boolean }>
) {
  useEnterQueueMock.mockReturnValue({
    mutate,
    data: undefined,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof useEnterQueue>);
}

function mockQueueStatusReturn(
  refetch: jest.Mock,
  overrides: Partial<{
    data: QueueStatusResult;
    isError: boolean;
    consecutiveFailureCount: number;
  }>
) {
  useQueueStatusMock.mockReturnValue({
    data: undefined,
    isError: false,
    consecutiveFailureCount: 0,
    refetch,
    ...overrides,
  } as unknown as ReturnType<typeof useQueueStatus>);
}

/**
 * renderHook의 rerender는 props 재전달로 재렌더를 트리거한다. useWaitingRoom 자체는 이
 * tick prop을 쓰지 않고 클로저의 type/targetId만 사용하므로, mockEnterQueueReturn/
 * mockQueueStatusReturn으로 모킹 반환값을 바꾼 뒤 rerender({ tick: n })을 호출해 다음
 * 렌더에서 바뀐 값을 읽게 한다.
 */
function renderWaitingRoom(type: QueueTargetType, targetId: number) {
  return renderHook(() => useWaitingRoom(type, targetId), {
    initialProps: { tick: 0 },
  });
}

let nextTick = 1;
function forceRerender(rerender: (props: { tick: number }) => void) {
  rerender({ tick: nextTick });
  nextTick += 1;
}

describe('useWaitingRoom', () => {
  let replaceMock: jest.Mock;
  let enterMutateMock: jest.Mock;
  let statusRefetchMock: jest.Mock;

  beforeEach(() => {
    useEntryTokenStore.setState({ tokens: {} });
    replaceMock = jest.fn();
    useRouterMock.mockReturnValue({
      push: jest.fn(),
      replace: replaceMock,
      back: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);

    enterMutateMock = jest.fn();
    statusRefetchMock = jest.fn();
    mockEnterQueueReturn(enterMutateMock, {});
    mockQueueStatusReturn(statusRefetchMock, {});
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('마운트 시 대기열에 1회 진입하고, 첫 응답 전에는 loading이다', () => {
    const { result } = renderWaitingRoom('limited-drop', 42);

    expect(result.current.phase).toBe('loading');
    expect(enterMutateMock).toHaveBeenCalledTimes(1);
    expect(useQueueStatusMock).toHaveBeenLastCalledWith('limited-drop', 42, 7, false);
  });

  it('WAITING 응답에서 순번·앞선 인원·ETA·진행바를 렌더한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'WAITING',
          position: 1240,
          aheadCount: 1239,
          etaSeconds: 240,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('waiting'));
    if (result.current.phase !== 'waiting') {
      throw new Error('unreachable');
    }
    expect(result.current.position).toBe(1240);
    expect(result.current.aheadCount).toBe(1239);
    expect(result.current.etaLabel).toBe('약 4분');
    expect(result.current.ratio).toBe(0);
    expect(result.current.percentLabel).toBe('0%');
    expect(useQueueStatusMock).toHaveBeenLastCalledWith('limited-drop', 42, 7, true);
  });

  it('최초 aheadCount 기준으로 진행바 ratio를 계산하고, 재유입으로 늘어나도 음수 대신 0으로 clamp한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'WAITING',
          position: 1000,
          aheadCount: 1000,
          etaSeconds: 300,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => expect(result.current.phase).toBe('waiting'));

    mockQueueStatusReturn(statusRefetchMock, {
      data: {
        outcome: 'OK',
        data: {
          status: 'WAITING',
          position: 500,
          aheadCount: 500,
          etaSeconds: 150,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => {
      if (result.current.phase !== 'waiting') throw new Error('not waiting yet');
      expect(result.current.ratio).toBeCloseTo(0.5, 5);
    });

    mockQueueStatusReturn(statusRefetchMock, {
      data: {
        outcome: 'OK',
        data: {
          status: 'WAITING',
          position: 1300,
          aheadCount: 1300,
          etaSeconds: 400,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => {
      if (result.current.phase !== 'waiting') throw new Error('not waiting yet');
      expect(result.current.ratio).toBe(0);
    });
  });

  it('ADMITTED 도달 시 entryTokenStore에 토큰을 저장하고 구매 경로로 replace한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'WAITING',
          position: 3,
          aheadCount: 2,
          etaSeconds: 20,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => expect(result.current.phase).toBe('waiting'));

    mockQueueStatusReturn(statusRefetchMock, {
      data: {
        outcome: 'OK',
        data: {
          status: 'ADMITTED',
          position: null,
          aheadCount: null,
          etaSeconds: null,
          entryToken: 'entry-token-abc',
          tokenExpiresAt: '2030-01-01T00:10:00Z',
        },
      },
    });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('admitted'));
    expect(useEntryTokenStore.getState().tokenFor('limited-drop', 42)).toBe('entry-token-abc');
    expect(replaceMock).toHaveBeenCalledWith(ROUTES.limitedDrop.purchase('42'));
  });

  it('DIRECT_ADMITTED(플래그 OFF) 시 대기 없이 즉시 티케팅 주문 경로로 전환한다', async () => {
    const { result, rerender } = renderWaitingRoom('ticketing-event', 7);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'DIRECT_ADMITTED',
          position: null,
          aheadCount: null,
          etaSeconds: null,
          entryToken: 'direct-token',
          tokenExpiresAt: '2030-01-01T00:05:00Z',
        },
      },
    });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('admitted'));
    expect(useEntryTokenStore.getState().tokenFor('ticketing-event', 7)).toBe('direct-token');
    expect(replaceMock).toHaveBeenCalledWith(ROUTES.event.order('7'));
    // DIRECT_ADMITTED는 WAITING을 거치지 않으므로 폴링이 활성화된 적이 없어야 한다.
    expect(useQueueStatusMock).not.toHaveBeenCalledWith('ticketing-event', 7, 7, true);
  });

  it('429 포화면 full 상태와 수동 재시도 CTA를 노출하고, 재시도는 재-enter한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, { data: { outcome: 'FULL' } });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('full'));
    const fullViewModel = result.current;
    if (fullViewModel.phase !== 'full') {
      throw new Error('unreachable');
    }
    act(() => {
      fullViewModel.retry();
    });
    expect(enterMutateMock).toHaveBeenCalledTimes(2);
  });

  it('404(NOT_IN_QUEUE)면 empty 상태를 노출하고, 재시도는 재-enter한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'WAITING',
          position: 3,
          aheadCount: 2,
          etaSeconds: 20,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => expect(result.current.phase).toBe('waiting'));

    mockQueueStatusReturn(statusRefetchMock, { data: { outcome: 'NOT_IN_QUEUE' } });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('empty'));
    const emptyViewModel = result.current;
    if (emptyViewModel.phase !== 'empty') {
      throw new Error('unreachable');
    }
    act(() => {
      emptyViewModel.retry();
    });
    expect(enterMutateMock).toHaveBeenCalledTimes(2);
  });

  it('폴링이 연속 3회 실패해야 error 상태로 전이하고(그 전 1~2회는 waiting 유지), 재시도는 상태를 재조회한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'WAITING',
          position: 3,
          aheadCount: 2,
          etaSeconds: 20,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => expect(result.current.phase).toBe('waiting'));

    // 1~2회 연속 실패는 관용 — error로 전이하지 않고 waiting을 유지한다(자동 폴링 계속).
    mockQueueStatusReturn(statusRefetchMock, {
      data: undefined,
      isError: true,
      consecutiveFailureCount: 1,
    });
    forceRerender(rerender);
    expect(result.current.phase).toBe('waiting');

    mockQueueStatusReturn(statusRefetchMock, {
      data: undefined,
      isError: true,
      consecutiveFailureCount: 2,
    });
    forceRerender(rerender);
    expect(result.current.phase).toBe('waiting');

    // 연속 3회째(MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES)에 도달하면 error로 전이한다.
    mockQueueStatusReturn(statusRefetchMock, {
      data: undefined,
      isError: true,
      consecutiveFailureCount: 3,
    });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('error'));
    const statusErrorViewModel = result.current;
    if (statusErrorViewModel.phase !== 'error') {
      throw new Error('unreachable');
    }
    act(() => {
      statusErrorViewModel.retry();
    });
    expect(statusRefetchMock).toHaveBeenCalledTimes(1);
    expect(enterMutateMock).toHaveBeenCalledTimes(1);
  });

  it('폴링이 1회 성공한 뒤 지속 실패해도(직전 성공 data 보존) 연속 3회에 도달하면 stale 순번 프리즈 없이 error로 전이한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, {
      data: {
        outcome: 'ENTERED',
        data: {
          status: 'WAITING',
          position: 10,
          aheadCount: 9,
          etaSeconds: 60,
          entryToken: null,
          tokenExpiresAt: null,
        },
      },
    });
    forceRerender(rerender);
    await waitFor(() => expect(result.current.phase).toBe('waiting'));

    const lastSuccessfulStatus: QueueStatusResult = {
      outcome: 'OK',
      data: {
        status: 'WAITING',
        position: 8,
        aheadCount: 7,
        etaSeconds: 50,
        entryToken: null,
        tokenExpiresAt: null,
      },
    };
    mockQueueStatusReturn(statusRefetchMock, { data: lastSuccessfulStatus });
    forceRerender(rerender);
    await waitFor(() => {
      if (result.current.phase !== 'waiting') throw new Error('not waiting yet');
      expect(result.current.position).toBe(8);
    });

    // TanStack Query는 성공 이후의 실패에서도 직전 성공 data를 보존한다(isError=true여도
    // data는 stale 값 그대로 남는다). 연속 실패 1~2회는 여전히 waiting(직전 순번 8)을 유지해야 한다.
    mockQueueStatusReturn(statusRefetchMock, {
      data: lastSuccessfulStatus,
      isError: true,
      consecutiveFailureCount: 1,
    });
    forceRerender(rerender);
    expect(result.current.phase).toBe('waiting');

    mockQueueStatusReturn(statusRefetchMock, {
      data: lastSuccessfulStatus,
      isError: true,
      consecutiveFailureCount: 2,
    });
    forceRerender(rerender);
    expect(result.current.phase).toBe('waiting');

    // 연속 3회째에 도달하면 data가 여전히(stale) 남아있어도 error로 전이한다 — 프리즈 방지.
    mockQueueStatusReturn(statusRefetchMock, {
      data: lastSuccessfulStatus,
      isError: true,
      consecutiveFailureCount: 3,
    });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('error'));
  });

  it('enter 단계 5xx 실패 시 error 상태를 노출하고, 재시도는 재-enter한다', async () => {
    const { result, rerender } = renderWaitingRoom('limited-drop', 42);

    mockEnterQueueReturn(enterMutateMock, { data: undefined, isError: true });
    forceRerender(rerender);

    await waitFor(() => expect(result.current.phase).toBe('error'));
    const enterErrorViewModel = result.current;
    if (enterErrorViewModel.phase !== 'error') {
      throw new Error('unreachable');
    }
    act(() => {
      enterErrorViewModel.retry();
    });
    expect(enterMutateMock).toHaveBeenCalledTimes(2);
  });

  it('leave()는 대기열 나가기를 best-effort로 호출한다', () => {
    const { result } = renderWaitingRoom('limited-drop', 42);

    act(() => {
      result.current.leave();
    });

    expect(leaveQueueMock).toHaveBeenCalledWith('limited-drop', 42, 7);
  });
});
