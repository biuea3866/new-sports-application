/**
 * WaitingRoomScreen(S1) — phase별 렌더링·자동전환·나가기/언마운트 leave·라이트/다크 두 모드 검증.
 * 근거: 티켓 FE-07 "테스트 케이스" · design-fe-app.md "화면별 상태 표" · "S1 텍스트 와이어프레임"
 *
 * useWaitingRoom을 모킹해 화면의 phase 분기 렌더링만 사용자 관점(보이는 텍스트·role)으로 검증한다.
 * 자동 전환(router.replace) 자체는 useWaitingRoom(FE-07 뷰모델) 테스트가 이미 검증한다 — 여기서는
 * admitted phase의 렌더와, 나가기/언마운트 시 leave 호출만 확인한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { WaitingRoomPhase } from '../../../../lib/useWaitingRoom';
import WaitingRoomScreen from '../[targetId]';

jest.mock('../../../../lib/useWaitingRoom', () => ({
  useWaitingRoom: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useWaitingRoom } from '../../../../lib/useWaitingRoom';

const useWaitingRoomMock = useWaitingRoom as jest.MockedFunction<typeof useWaitingRoom>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

// Omit은 유니온에 분배되지 않으므로, phase별 필드(position/retry 등)를 살리기 위해 직접 분배한다.
type DistributiveOmitLeave<T> = T extends { leave: () => void } ? Omit<T, 'leave'> : never;
type ViewModelInput = DistributiveOmitLeave<WaitingRoomPhase> & { leave?: jest.Mock };

function mockViewModel(partial: ViewModelInput) {
  const leave = partial.leave ?? jest.fn();
  useWaitingRoomMock.mockReturnValue({ ...partial, leave } as unknown as WaitingRoomPhase);
  return leave;
}

describe('WaitingRoomScreen', () => {
  let backMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ type: 'limited-drop', targetId: '42' });
    backMock = jest.fn();
    useRouterMock.mockReturnValue({
      push: jest.fn(),
      replace: jest.fn(),
      back: backMock,
    } as unknown as ReturnType<typeof useRouter>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('loading phase에서 대기열에 들어가는 중임을 안내한다', () => {
    mockViewModel({ phase: 'loading' });

    render(<WaitingRoomScreen />);

    expect(screen.getByLabelText('대기열에 들어가는 중')).toBeTruthy();
  });

  it('waiting phase에서 순번·앞선 인원·ETA·진행바를 렌더한다', () => {
    mockViewModel({
      phase: 'waiting',
      position: 1240,
      aheadCount: 1239,
      etaLabel: '약 4분',
      ratio: 0.62,
      percentLabel: '62%',
    });

    render(<WaitingRoomScreen />);

    expect(screen.getByText('잠시만 기다려 주세요')).toBeTruthy();
    expect(screen.getByLabelText('내 순번 1240번')).toBeTruthy();
    expect(screen.getByText('앞선 대기 1,239명')).toBeTruthy();
    expect(screen.getByText('예상 대기 약 4분')).toBeTruthy();
    expect(screen.getByLabelText('대기 진행률 62%')).toBeTruthy();
  });

  it('admitted phase에서 입장 이동 중 상태를 렌더한다(자동전환은 뷰모델이 수행)', () => {
    mockViewModel({ phase: 'admitted' });

    render(<WaitingRoomScreen />);

    expect(screen.getByLabelText('입장! 이동 중')).toBeTruthy();
  });

  it('empty(404) phase에서 안내와 다시 대기 CTA를 렌더하고 탭 시 retry를 호출한다', () => {
    const retry = jest.fn();
    mockViewModel({ phase: 'empty', retry });

    render(<WaitingRoomScreen />);

    expect(screen.getByText('대기열에서 나왔어요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('다시 대기'));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('full(429) phase에서 포화 안내와 다시 시도 CTA를 렌더하고 탭 시 retry를 호출한다', () => {
    const retry = jest.fn();
    mockViewModel({ phase: 'full', retry });

    render(<WaitingRoomScreen />);

    expect(screen.getByText('지금 대기 인원이 많아요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('다시 시도'));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('error phase에서 오류 안내와 재시도를 렌더하고 탭 시 retry를 호출한다', () => {
    const retry = jest.fn();
    mockViewModel({ phase: 'error', retry });

    render(<WaitingRoomScreen />);

    expect(screen.getByText('연결이 불안정해요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('다시 시도'));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('나가기를 누르면 leave를 호출하고 이전 화면으로 돌아간다', () => {
    const leave = mockViewModel({
      phase: 'waiting',
      position: 1,
      aheadCount: 0,
      etaLabel: null,
      ratio: null,
      percentLabel: null,
    });

    render(<WaitingRoomScreen />);
    fireEvent.press(screen.getByLabelText('나가기'));

    expect(leave).toHaveBeenCalledTimes(1);
    expect(backMock).toHaveBeenCalledTimes(1);
  });

  it('언마운트 시에도 leave를 best-effort 호출한다', () => {
    const leave = mockViewModel({ phase: 'loading' });

    const { unmount } = render(<WaitingRoomScreen />);
    unmount();

    expect(leave).toHaveBeenCalledTimes(1);
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockViewModel({
      phase: 'waiting',
      position: 5,
      aheadCount: 4,
      etaLabel: '약 1분',
      ratio: 0.2,
      percentLabel: '20%',
    });

    render(<WaitingRoomScreen />);

    expect(screen.getByLabelText('내 순번 5번')).toBeTruthy();
  });
});
