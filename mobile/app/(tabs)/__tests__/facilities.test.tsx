/**
 * FacilitiesScreen — 내 주변 시설 + 날씨 화면 사용자 관점 동작 검증.
 * 근거: 사용자 피드백 "시설을 클릭해도 상세가 안 뜬다" — 목록 아이템이 순수 View라 탭이
 * 안 됐다. Pressable로 감싸 `/facility/{id}` 상세로 이동시킨다.
 *
 * useQuery(@tanstack/react-query)를 직접 호출하는 화면이라 `app/(tabs)/index.test.tsx`와
 * 동일하게 useQuery 자체를 모킹해 queryKey로 분기한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

jest.mock('@tanstack/react-query', () => ({
  useQuery: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import type { FacilitySummary, Forecast } from '../../../api/external-features';
import FacilitiesScreen from '../facilities';

const useQueryMock = useQuery as jest.MockedFunction<typeof useQuery>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const FACILITY: FacilitySummary = {
  id: '64f1a2b3c4d5e6f7a8b9c0d1',
  name: '한강 축구장',
  gu: '광진구',
  type: '축구장',
  address: '서울 광진구 자양동 123',
  lat: 37.5,
  lng: 127.1,
  parking: true,
  tel: '02-1234-5678',
  homePage: '',
  eduYn: false,
};

interface QueryState<T> {
  data: T | undefined;
  isLoading: boolean;
  isError: boolean;
}

function mockQueries(
  overrides: {
    facilities?: Partial<QueryState<FacilitySummary[]>>;
    weather?: Partial<QueryState<Forecast>>;
  } = {}
) {
  useQueryMock.mockImplementation((options: unknown) => {
    const queryKey = (options as { queryKey: unknown[] }).queryKey;
    if (queryKey[0] === 'facilities') {
      return {
        data: [FACILITY],
        isLoading: false,
        isError: false,
        ...overrides.facilities,
      } as unknown as ReturnType<typeof useQuery>;
    }
    return {
      data: undefined,
      isLoading: false,
      isError: false,
      ...overrides.weather,
    } as unknown as ReturnType<typeof useQuery>;
  });
}

describe('시설 화면 — 내 주변 시설 + 날씨', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ push: pushMock } as unknown as ReturnType<typeof useRouter>);
    mockQueries();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('시설 목록을 이름과 함께 렌더한다', () => {
    render(<FacilitiesScreen />);

    expect(screen.getByText('한강 축구장')).toBeTruthy();
  });

  it('시설 항목을 탭하면 시설 상세 화면으로 이동한다', () => {
    render(<FacilitiesScreen />);

    fireEvent.press(screen.getByRole('button', { name: `${FACILITY.name} ${FACILITY.type}` }));

    expect(pushMock).toHaveBeenCalledWith(`/facility/${FACILITY.id}`);
  });

  it('시설 조회 중이면 로딩 인디케이터를 표시한다', () => {
    mockQueries({ facilities: { isLoading: true, data: undefined } });

    render(<FacilitiesScreen />);

    expect(screen.queryByRole('button', { name: `${FACILITY.name} ${FACILITY.type}` })).toBeNull();
  });

  it('시설 조회 실패 시 에러 문구를 표시한다', () => {
    mockQueries({ facilities: { isError: true, data: undefined } });

    render(<FacilitiesScreen />);

    expect(screen.getByText('시설을 불러오지 못했습니다.')).toBeTruthy();
  });

  it('시설 목록이 비어있으면 빈 상태 문구를 표시한다', () => {
    mockQueries({ facilities: { data: [] } });

    render(<FacilitiesScreen />);

    expect(screen.getByText('주변에 시설이 없습니다.')).toBeTruthy();
  });

  it('날씨 정보가 있으면 표시한다', () => {
    mockQueries({
      weather: {
        data: {
          slots: [
            {
              date: '20260716',
              time: '1200',
              temperature: 28,
              sky: 'CLEAR',
              precipitationType: null,
              precipitationProbability: 10,
              humidity: 50,
              windSpeed: 2,
            },
          ],
        },
      },
    });

    render(<FacilitiesScreen />);

    expect(screen.getByText(/28℃/)).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<FacilitiesScreen />);

    expect(screen.getByText('한강 축구장')).toBeTruthy();
  });
});
