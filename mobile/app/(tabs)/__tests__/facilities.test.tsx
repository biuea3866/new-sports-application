/**
 * FacilitiesScreen — 내 주변 시설 + 날씨 + 지도 화면 사용자 관점 동작 검증.
 * 근거: 사용자 피드백 "search 탭 네이밍이 이해 안 된다" — 이 탭은 실제로 내 주변
 * 시설 검색(`/facilities/near` + 날씨)이므로 라벨을 "시설"로, 라우트 파일명도
 * `search.tsx` → `facilities.tsx`로 바꿨다.
 *
 * 사용자 요청 "UI에 맵도 띄워줘": 목록 상단에 FacilityMap을 추가했다. FacilityMap은
 * 웹(Leaflet)/네이티브(react-native-maps) 구현이 갈리므로 이 화면 테스트에서는
 * 실제 지도 라이브러리를 건드리지 않도록 컴포넌트 자체를 mock한다 — 지도 라이브러리별
 * 마커·fallback 검증은 components/map/__tests__에 있다.
 *
 * useQuery(@tanstack/react-query)를 직접 호출하는 화면이라 useQuery 자체를 모킹해
 * queryKey로 분기한다.
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

jest.mock('../../../lib/useCurrentLocation', () => ({
  useCurrentLocation: jest.fn(),
}));

jest.mock('../../../components/map/FacilityMap', () => {
  const ReactModule = require('react'); // eslint-disable-line @typescript-eslint/no-var-requires
  const { View, Text, Pressable } = require('react-native'); // eslint-disable-line @typescript-eslint/no-var-requires
  return {
    __esModule: true,
    FacilityMap: (props: {
      facilities: { id: string; name: string }[];
      onMarkerPress: (id: string) => void;
    }) =>
      ReactModule.createElement(
        View,
        { testID: 'mock-facility-map' },
        ReactModule.createElement(Text, null, `markers:${props.facilities.length}`),
        props.facilities.map((facility) =>
          ReactModule.createElement(
            Pressable,
            {
              key: facility.id,
              testID: `mock-marker-${facility.id}`,
              onPress: () => props.onMarkerPress(facility.id),
            },
            null
          )
        )
      ),
  };
});

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useCurrentLocation } from '../../../lib/useCurrentLocation';
import type { FacilitySummary, Forecast } from '../../../api/external-features';
import FacilitiesScreen from '../facilities';

const useQueryMock = useQuery as jest.MockedFunction<typeof useQuery>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const useCurrentLocationMock = useCurrentLocation as jest.MockedFunction<typeof useCurrentLocation>;

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

describe('시설 화면 — 내 주변 시설 + 날씨 + 지도', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ push: pushMock } as unknown as ReturnType<typeof useRouter>);
    useCurrentLocationMock.mockReturnValue({ lat: 37.4979, lng: 127.0276, isDefault: true });
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

  it('시설 조회 성공 시 시설 개수만큼 지도 마커를 요청한다', () => {
    render(<FacilitiesScreen />);

    expect(screen.getByTestId('mock-facility-map')).toBeTruthy();
    expect(screen.getByText('markers:1')).toBeTruthy();
  });

  it('지도 마커를 탭하면 시설 상세 화면으로 이동한다', () => {
    render(<FacilitiesScreen />);

    fireEvent.press(screen.getByTestId(`mock-marker-${FACILITY.id}`));

    expect(pushMock).toHaveBeenCalledWith(`/facility/${FACILITY.id}`);
  });

  it('시설 조회 중에는 지도를 렌더하지 않는다', () => {
    mockQueries({ facilities: { isLoading: true, data: undefined } });

    render(<FacilitiesScreen />);

    expect(screen.queryByTestId('mock-facility-map')).toBeNull();
  });
});
