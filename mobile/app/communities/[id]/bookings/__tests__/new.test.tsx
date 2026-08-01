/**
 * CommunityBookingLinkScreen(A-B2) — 소모임 예약 연결 화면 사용자 관점 동작 검증.
 * 근거: design-fe-app.md Testing Plan "결제 재사용" 인접, "화면별 4상태 표" A-B2.
 *
 * useFacilityOptions·useSlots·useLinkCommunityBooking을 모킹해 화면 배선만 검증한다.
 * 시설은 내부 식별자 입력이 아니라 목록에서 고른다 — 사용자가 시설 ID를 알 리 없다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { FacilityResponse, SlotResponse } from '../../../../../api/types';
import CommunityBookingLinkScreen from '../new';

jest.mock('../../../../../lib/useBooking', () => ({
  useSlots: jest.fn(),
}));

jest.mock('../../../../../lib/useFacility', () => ({
  useFacilityOptions: jest.fn(),
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
import { useFacilityOptions } from '../../../../../lib/useFacility';
import { useLinkCommunityBooking } from '../../../../../lib/useCommunityBooking';

const useSlotsMock = useSlots as jest.MockedFunction<typeof useSlots>;
const useFacilityOptionsMock = useFacilityOptions as jest.MockedFunction<typeof useFacilityOptions>;
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

const GANGNAM_FACILITY: FacilityResponse = {
  id: '10',
  name: '강남 스포츠센터',
  gu: '강남구',
  type: '풋살장',
  address: '서울 강남구 테헤란로 152',
  parking: true,
  tel: '02-555-0101',
  lat: 37.5,
  lng: 127.1,
  sidoCode: '11',
  sidoName: '서울특별시',
  sigunguCode: '11680',
  sigunguName: '강남구',
};

const SONGPA_FACILITY: FacilityResponse = {
  ...GANGNAM_FACILITY,
  id: '20',
  name: '송파 배드민턴장',
  gu: '송파구',
  type: '배드민턴장',
  sigunguCode: '11710',
  sigunguName: '송파구',
};

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

function mockFacilityOptions(overrides: Partial<ReturnType<typeof useFacilityOptions>> = {}) {
  useFacilityOptionsMock.mockReturnValue({
    data: [GANGNAM_FACILITY, SONGPA_FACILITY],
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useFacilityOptions>);
}

function mockLinkMutation(overrides: Record<string, unknown> = {}) {
  useLinkCommunityBookingMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useLinkCommunityBooking>);
}

/** 시설 목록에서 강남 스포츠센터를 골라 회차 목록 단계로 진입한다. */
function selectGangnamFacility() {
  fireEvent.press(screen.getByLabelText('강남 스포츠센터, 강남구 풋살장'));
}

describe('CommunityBookingLinkScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '5' });
    mockSlots({ data: [] });
    mockFacilityOptions();
    mockLinkMutation();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('사용자에게 시설 내부 식별자 입력을 요구하지 않는다', () => {
    render(<CommunityBookingLinkScreen />);

    expect(screen.queryByText('시설 ID')).toBeNull();
    expect(screen.queryByLabelText('시설 ID 입력')).toBeNull();
  });

  it('시설을 고르기 전에는 시설 목록과 안내 문구가 표시된다', () => {
    render(<CommunityBookingLinkScreen />);

    expect(screen.getByText('시설을 선택하면 예약 가능한 회차가 표시돼요')).toBeTruthy();
    expect(screen.getByLabelText('강남 스포츠센터, 강남구 풋살장')).toBeTruthy();
    expect(screen.getByLabelText('송파 배드민턴장, 송파구 배드민턴장')).toBeTruthy();
  });

  it('검색어를 입력하면 이름·지역으로 시설 목록이 좁혀진다', () => {
    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 검색'), '송파');

    expect(screen.queryByLabelText('강남 스포츠센터, 강남구 풋살장')).toBeNull();
    expect(screen.getByLabelText('송파 배드민턴장, 송파구 배드민턴장')).toBeTruthy();
  });

  it('검색 결과가 없으면 빈 상태를 표시한다', () => {
    render(<CommunityBookingLinkScreen />);
    fireEvent.changeText(screen.getByLabelText('시설 검색'), '존재하지 않는 시설');

    expect(screen.getByText('조건에 맞는 시설이 없어요')).toBeTruthy();
  });

  it('시설 목록 조회에 실패하면 에러 뷰가 표시되고 재시도할 수 있다', () => {
    const refetch = jest.fn();
    mockFacilityOptions({ data: undefined, isError: true, refetch });

    render(<CommunityBookingLinkScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('시설 목록 로딩 중에는 로딩 뷰가 표시된다', () => {
    mockFacilityOptions({ data: undefined, isLoading: true });

    render(<CommunityBookingLinkScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('시설을 선택하면 선택한 시설 이름이 표시되고 회차를 조회한다', () => {
    mockSlots({ data: [OPEN_SLOT] });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();

    expect(screen.getByText('강남 스포츠센터')).toBeTruthy();
    expect(useSlotsMock).toHaveBeenCalledWith('10');
  });

  it('시설을 다시 고를 수 있다', () => {
    mockSlots({ data: [OPEN_SLOT] });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();
    fireEvent.press(screen.getByLabelText('시설 다시 선택'));

    expect(screen.getByLabelText('시설 검색')).toBeTruthy();
  });

  it('회차 로딩 중이면 로딩 뷰가 표시된다', () => {
    mockSlots({ data: undefined, isLoading: true });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('CLOSED 슬롯은 제외하고 OPEN 슬롯만 목록에 표시된다', () => {
    mockSlots({ data: [OPEN_SLOT, CLOSED_SLOT] });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();

    expect(screen.getByLabelText(/14:00~15:00/)).toBeTruthy();
  });

  it('예약 가능한 회차가 없으면 빈 상태를 표시한다', () => {
    mockSlots({ data: [CLOSED_SLOT] });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();

    expect(screen.getByText('예약 가능한 회차가 없어요')).toBeTruthy();
  });

  it('슬롯 조회 실패 시 에러 뷰가 표시되고 재시도할 수 있다', () => {
    const refetch = jest.fn();
    mockSlots({ data: undefined, isError: true, refetch });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('슬롯을 선택하지 않으면 연결 CTA가 비활성 상태다', () => {
    mockSlots({ data: [OPEN_SLOT] });

    render(<CommunityBookingLinkScreen />);
    selectGangnamFacility();

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
    selectGangnamFacility();
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
    selectGangnamFacility();
    fireEvent.press(screen.getByLabelText(/14:00~15:00/));
    fireEvent.press(screen.getByLabelText('이 회차로 연결'));

    expect(screen.getByText('방장만 연결할 수 있어요')).toBeTruthy();
  });

  it('facilityId 쿼리로 진입하면 그 시설이 미리 선택된다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: '5', facilityId: '20' });
    mockSlots({ data: [OPEN_SLOT] });

    render(<CommunityBookingLinkScreen />);

    expect(screen.getByText('송파 배드민턴장')).toBeTruthy();
    expect(useSlotsMock).toHaveBeenCalledWith('20');
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<CommunityBookingLinkScreen />);

    expect(screen.getByText('활동 예약 연결')).toBeTruthy();
  });
});
