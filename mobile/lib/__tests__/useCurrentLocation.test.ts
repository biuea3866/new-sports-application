/**
 * useCurrentLocation(네이티브 기본) — 시설 지도의 중심 좌표 해석 훅.
 *
 * 네이티브는 아직 위치 권한 연동 전(기존 facilities.tsx 주석 "위치 권한 연동은
 * 후속 작업")이라 항상 기본 좌표를 반환한다. 후속 티켓이 expo-location을 연동하면
 * 이 파일(확장자 없는 기본 구현)에 권한 요청을 추가한다.
 */
import { renderHook } from '@testing-library/react-native';
import { useCurrentLocation } from '../useCurrentLocation';

describe('useCurrentLocation(네이티브)', () => {
  it('항상 기본 좌표를 isDefault=true로 반환한다', () => {
    const { result } = renderHook(() => useCurrentLocation({ lat: 37.4979, lng: 127.0276 }));

    expect(result.current).toEqual({ lat: 37.4979, lng: 127.0276, isDefault: true });
  });
});
