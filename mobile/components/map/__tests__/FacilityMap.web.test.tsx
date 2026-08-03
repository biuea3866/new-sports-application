/**
 * FacilityMap(웹, Leaflet) — 사용자 관점 동작 검증.
 *
 * Leaflet은 실 DOM 지도 렌더(타일 로드·좌표계 연산)를 수행하므로 jsdom 테스트
 * 환경에서 신뢰할 수 없다. `leaflet` 모듈 자체를 mock해 "시설 개수만큼 마커를
 * 생성하는지 / 마커 클릭 시 onMarkerPress를 호출하는지 / 표시할 시설이 없을 때
 * fallback 문구를 렌더하는지"를 컴포넌트 로직 수준에서 검증한다.
 */
import React from 'react';
import { act, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

interface MockMarker {
  addTo: jest.Mock;
  bindPopup: jest.Mock;
  on: jest.Mock;
  remove: jest.Mock;
  _clickHandler?: () => void;
}

interface MockLeafletMap {
  setView: jest.Mock;
  remove: jest.Mock;
}

interface MockTileLayer {
  addTo: jest.Mock;
  setUrl: jest.Mock;
  on: jest.Mock;
  _errorHandler?: () => void;
}

jest.mock('leaflet', () => {
  const mapInstance: MockLeafletMap = {
    setView: jest.fn(() => mapInstance),
    remove: jest.fn(),
  };

  return {
    __esModule: true,
    default: {
      map: jest.fn(() => mapInstance),
      tileLayer: jest.fn(() => {
        const layer: MockTileLayer = {
          addTo: jest.fn(() => layer),
          setUrl: jest.fn(() => layer),
          on: jest.fn((event: string, handler: () => void) => {
            if (event === 'tileerror') {
              layer._errorHandler = handler;
            }
            return layer;
          }),
        };
        return layer;
      }),
      marker: jest.fn(() => {
        const marker: MockMarker = {
          addTo: jest.fn(() => marker),
          bindPopup: jest.fn(() => marker),
          on: jest.fn((event: string, handler: () => void) => {
            if (event === 'click') {
              marker._clickHandler = handler;
            }
            return marker;
          }),
          remove: jest.fn(),
        };
        return marker;
      }),
      // 컴포넌트가 모듈 로드 시 기본 마커 아이콘을 CDN URL로 재지정한다(FacilityMap.web.tsx
      // 상단 주석 참조) — mock도 동일한 형태(Marker.prototype.options)를 제공해야 한다.
      icon: jest.fn((options: Record<string, unknown>) => options),
      Marker: { prototype: { options: {} as Record<string, unknown> } },
    },
  };
});

// leaflet/dist/leaflet.css는 jest.config.js moduleNameMapper('\\.css$')가 빈 모듈로 치환한다.

import L from 'leaflet';
import { FacilityMap } from '../FacilityMap.web';
import type { MapFacility } from '../types';

const markerMock = L.marker as unknown as jest.Mock<MockMarker, [[number, number]]>;
const tileLayerMock = L.tileLayer as unknown as jest.Mock<MockTileLayer, [string, object]>;
const mapMock = L.map as unknown as jest.Mock;

function createdTileLayers(): MockTileLayer[] {
  return tileLayerMock.mock.results.map((result) => result.value as MockTileLayer);
}

function createdMarkers(): MockMarker[] {
  return markerMock.mock.results.map((result) => result.value as MockMarker);
}

const FACILITIES: MapFacility[] = [
  { id: 'f1', name: '한강 축구장', lat: 37.5, lng: 127.1 },
  { id: 'f2', name: '잠실 야구장', lat: 37.51, lng: 127.07 },
];

describe('FacilityMap(웹) — Leaflet 기반 지도', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    markerMock.mockClear();
    tileLayerMock.mockClear();
    mapMock.mockClear();
  });

  it('시설 개수만큼 마커를 생성한다', () => {
    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    expect(createdMarkers()).toHaveLength(FACILITIES.length);
  });

  // 지도 타일도 화면의 일부다 — 다크 모드에서 밝은 OSM 타일만 쓰면 화면이 반반으로 갈린다.
  it('라이트 모드에서는 라이트 타일을 사용한다', () => {
    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    const tileUrl = tileLayerMock.mock.calls[0][0];
    expect(tileUrl).toContain('light_all');
  });

  it('다크 모드에서는 다크 타일을 사용한다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    const tileUrl = tileLayerMock.mock.calls[0][0];
    expect(tileUrl).toContain('dark_all');
  });

  // 테마 토글로 지도를 재생성하면 사용자가 이동·확대해 둔 위치가 초기값으로 되돌아간다.
  it('테마가 바뀌어도 지도를 재생성하지 않고 타일 URL만 교체한다', () => {
    const { rerender } = render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );
    expect(mapMock).toHaveBeenCalledTimes(1);

    mockUseColorScheme.mockReturnValue('dark');
    rerender(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    expect(mapMock).toHaveBeenCalledTimes(1);
    const setUrlCalls = createdTileLayers().flatMap((layer) => layer.setUrl.mock.calls);
    expect(setUrlCalls).toHaveLength(1);
    expect(setUrlCalls[0][0] as string).toContain('dark_all');
  });

  // 새 서드파티 타일(basemaps.cartocdn.com)이 죽으면 "빈 회색 판 위 마커"만 남는 조용한 실패가 된다.
  it('타일 로드가 실패하면 표준 OSM 타일로 폴백한다', () => {
    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={jest.fn()}
      />
    );

    const layer = createdTileLayers()[0];
    act(() => {
      layer._errorHandler?.();
    });

    expect(layer.setUrl).toHaveBeenCalledWith(expect.stringContaining('tile.openstreetmap.org'));
  });

  it('마커 클릭 시 onMarkerPress에 시설 id를 전달한다', () => {
    const onMarkerPress = jest.fn();
    render(
      <FacilityMap
        facilities={FACILITIES}
        center={{ lat: 37.4979, lng: 127.0276 }}
        onMarkerPress={onMarkerPress}
      />
    );

    createdMarkers()[0]?._clickHandler?.();

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
    expect(createdMarkers()).toHaveLength(0);
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
