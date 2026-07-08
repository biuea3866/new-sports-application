/**
 * CommunityBookingLinkScreen(A-B2) — 소모임 예약 연결 화면 사용자 관점 동작 검증.
 * 근거: design-fe-app.md Testing Plan "결제 재사용" 인접, "화면별 4상태 표" A-B2.
 *
 * useSlots·useLinkCommunityBooking을 모킹해 화면 배선만 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { SlotResponse } from '../../../../../api/types';
import CommunityBookingLinkScreen from '../new';

jest.mock('../../../../../lib/useBooking', () => ({
  useSlots: jest.fn(),
}));

jest.mock('../../../../../lib/useCommunityBooking', () => ({
  useLinkCommunityBooking: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), back: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(),
}));

import { router, useLocalSearchParams } from 'expo-router';
import { useSlots } from '../../../../../lib/useBooking';
import { useLinkCommunityBooking } from '../../../../../lib/useCommunityBooking';

const useSlotsMock = useSlots as jest.MockedFunction<typeof useSlots>;
const useLinkCommunityBookingMock = useLinkCommunityBooking as jest.MockedFunction<
  typeof useLinkCommunityBooking
>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const routerBackMock = router.back as jest.MockedFunction<typeof router.back>;

function forbiddenError(): AxiosError {
  return new AxiosError('Forbidden', undefined, undefined, undefined, {
    status: 403,
    data: {},
    statusText: 'Forbidden',
    headers: {},
    config: {} as never,
  });
}

const OPEN_SLOT: SlotResponse = {
  id: 1,
  facilityId: '10',
  date: '2026-07-12T00:00:00Z',
  timeRange: '14:00~15:00',
  capacity: 8,
  ownerId: 99,
  status: 'OPEN',
  programId: null,
};

const CLOSED_SLOT: SlotResponse = {
  ...OPEN_SLOT,
  id: 2,
  status: 'CLOSED',
};

function mockSlots(overrides: Partial<ReturnType<typeof useSlots>>) {
  useSlotsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useSlots>);
}

function mockLinkMutation(overrides: Record<string, unknown> = {}) {
  useLinkCommunityBookingMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useLinkCommunityBooking>);
}

describe('CommunityBookingLinkScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '5' });
    mockSlots({ data: [] });
    mockLinkMutation();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('시설 ID를 입력하지 않으면 안내 문구만 표시된다', () => {
    render(<CommunityBookingLinkScreen />);

    expect(screen.getByText('시설을 선택하면 예약 가능한 회차가 표시돼요')).toBeTruthy();
  });

  it('시설 ID 입력 후 슬롯이 로딩 중이면 로딩 뷰가 표시된다', () => {
    mockSlots({ data: undefined, isLoading: true });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('CLOSED 슬롯은 제외하고 OPEN 슬롯만 목록에 표시된다', () => {
    mockSlots({ data: [OPEN_SLOT, CLOSED_SLOT] });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');

    expect(screen.getByLabelText(/14:00~15:00/)).toBeTruthy();
  });

  it('예약 가능한 회차가 없으면 빈 상태를 표시한다', () => {
    mockSlots({ data: [CLOSED_SLOT] });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');

    expect(screen.getByText('예약 가능한 회차가 없어요')).toBeTruthy();
  });

  it('슬롯 조회 실패 시 에러 뷰가 표시되고 재시도할 수 있다', () => {
    const refetch = jest.fn();
    mockSlots({ data: undefined, isError: true, refetch });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('슬롯을 선택하지 않으면 연결 CTA가 비활성 상태다', () => {
    mockSlots({ data: [OPEN_SLOT] });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');

    const cta = screen.getByLabelText('이 회차로 연결');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });

  it('슬롯 선택 후 연결하면 성공 시 이전 화면으로 돌아간다', () => {
    mockSlots({ data: [OPEN_SLOT] });
    const mutate = jest.fn((_vars: { slotId: number }, options?: { onSuccess?: () => void }) =>
      options?.onSuccess?.()
    );
    mockLinkMutation({ mutate });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');
    fireEvent.press(screen.getByLabelText(/14:00~15:00/));
    fireEvent.press(screen.getByLabelText('이 회차로 연결'));

    expect(mutate).toHaveBeenCalledWith({ slotId: OPEN_SLOT.id }, expect.anything());
    expect(routerBackMock).toHaveBeenCalled();
  });

  it('403 오류면 방장만 연결할 수 있다는 안내가 표시된다', () => {
    mockSlots({ data: [OPEN_SLOT] });
    const mutate = jest.fn(
      (_vars: { slotId: number }, options?: { onError?: (error: unknown) => void }) =>
        options?.onError?.(forbiddenError())
    );
    mockLinkMutation({ mutate });

    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 ID 입력'), '10');
    fireEvent.press(screen.getByLabelText(/14:00~15:00/));
    fireEvent.press(screen.getByLabelText('이 회차로 연결'));

    expect(screen.getByText('방장만 연결할 수 있어요')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<CommunityBookingLinkScreen />);

    expect(screen.getByText('활동 예약 연결')).toBeTruthy();
  });
});
