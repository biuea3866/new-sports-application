/**
 * useCurrentLocation(웹) — navigator.geolocation 기반 현재 위치 조회, 실패 시 기본 좌표 폴백.
 */
import { renderHook, waitFor } from '@testing-library/react-native';
import { useCurrentLocation } from '../useCurrentLocation.web';

type GeolocationSuccess = (position: { coords: { latitude: number; longitude: number } }) => void;
type GeolocationError = (error: unknown) => void;

function mockGeolocation(
  implementation: (success: GeolocationSuccess, error: GeolocationError) => void
) {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: { getCurrentPosition: jest.fn(implementation) },
  });
}

describe('useCurrentLocation(웹)', () => {
  afterEach(() => {
    // @ts-expect-error 테스트 간 격리를 위해 geolocation을 되돌린다.
    delete navigator.geolocation;
  });

  it('geolocation 조회에 성공하면 기기 좌표를 반환한다', async () => {
    mockGeolocation((success) => {
      success({ coords: { latitude: 37.55, longitude: 127.0 } });
    });

    const { result } = renderHook(() => useCurrentLocation({ lat: 37.4979, lng: 127.0276 }));

    await waitFor(() => {
      expect(result.current).toEqual({ lat: 37.55, lng: 127.0, isDefault: false });
    });
  });

  it('geolocation 권한이 거부되면 기본 좌표로 폴백한다', async () => {
    mockGeolocation((_success, error) => {
      error(new Error('permission denied'));
    });

    const { result } = renderHook(() => useCurrentLocation({ lat: 37.4979, lng: 127.0276 }));

    await waitFor(() => {
      expect(result.current).toEqual({ lat: 37.4979, lng: 127.0276, isDefault: true });
    });
  });

  it('geolocation API 자체가 없으면 기본 좌표를 반환한다', () => {
    // @ts-expect-error 브라우저가 geolocation을 지원하지 않는 상황을 재현한다.
    delete navigator.geolocation;

    const { result } = renderHook(() => useCurrentLocation({ lat: 37.4979, lng: 127.0276 }));

    expect(result.current).toEqual({ lat: 37.4979, lng: 127.0276, isDefault: true });
  });
});
