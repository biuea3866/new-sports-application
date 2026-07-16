/**
 * FacilityMap(네이티브, react-native-maps) — 사용자 관점 동작 검증.
 *
 * jest-expo 기본 haste 플랫폼(ios/android/native)이 확장자 없는 `../FacilityMap`을
 * 이 파일(FacilityMap.tsx)로 해석한다 — `.web.tsx`는 별도 테스트(FacilityMap.web.test.tsx)에서
 * 명시적 경로로 검증한다.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

jest.mock('react-native-maps', () => {
  // jest.mock 팩토리는 out-of-scope 변수를 참조할 수 없어(babel-plugin-jest-hoist),
  // 팩토리 안에서 직접 require한다 — 이 파일에 한정된 예외.
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const ReactModule = require('react');
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { View } = require('react-native');

  const MockMapView = ({ children, testID, ...rest }: Record<string, unknown>) =>
    ReactModule.createElement(View, { testID: testID ?? 'mock-map-view', ...rest }, children);

  const MockMarker = ({
    onPress,
    testID,
    children,
  }: {
    onPress?: () => void;
    testID?: string;
    children?: React.ReactNode;
  }) =>
    ReactModule.createElement(
      View,
      { testID: testID ?? 'mock-marker', accessibilityRole: 'button', onTouchEnd: onPress },
      children
    );

  return {
    __esModule: true,
    default: MockMapView,
    Marker: MockMarker,
  };
});

import { FacilityMap } from '../FacilityMap';
import type { MapFacility } from '../types';

const FACILITIES: MapFacility[] = [
  { id: 'f1', name: '한강 축구장', lat: 37.5, lng: 127.1 },
  { id: 'f2', name: '잠실 야구장', lat: 37.51, lng: 127.07 },
];

describe('FacilityMap(네이티브) — react-native-maps 기반 지도', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('시설 개수만큼 마커를 렌더한다', () => {
    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    expect(screen.getAllByTestId('mock-marker')).toHaveLength(FACILITIES.length);
  });

  it('마커를 탭하면 onMarkerPress에 시설 id를 전달한다', () => {
    const onMarkerPress = jest.fn();
    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={onMarkerPress}
      />
    );

    fireEvent(screen.getAllByTestId('mock-marker')[0], 'touchEnd');

    expect(onMarkerPress).toHaveBeenCalledWith('f1');
  });

  it('표시할 시설이 없으면 지도 대신 안내 문구를 렌더한다', () => {
    render(
      <FacilityMap
        facilities={[]}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    expect(screen.getByText('주변에 표시할 시설이 없어요')).toBeTruthy();
    expect(screen.queryByTestId('mock-map-view')).toBeNull();
  });

  it('좌표가 유효하지 않은 시설만 있으면 안내 문구를 렌더한다', () => {
    render(
      <FacilityMap
        facilities={[{ id: 'bad', name: '좌표없음', lat: Number.NaN, lng: Number.NaN }]}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    expect(screen.getByText('주변에 표시할 시설이 없어요')).toBeTruthy();
  });
});
