/**
 * useAirQuality — 좌표 기준 대기질 조회 TanStack Query 훅 검증.
 *
 * U-01: 좌표가 주어지면 훅이 대기질 데이터를 반환한다
 * U-02: 좌표가 null이면 enabled=false로 조회하지 않는다
 * U-03: API 실패 시 훅이 에러 상태를 노출한다
 * U-04: BE UNKNOWN 응답을 success 데이터로 담는다(폴백은 컴포넌트 판단)
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import type { AirQualityResponse } from '../../api/types';
import { useAirQuality } from '../useAirQuality';

jest.mock('../../api/air-quality', () => ({
  getAirQuality: jest.fn(),
}));

import { getAirQuality } from '../../api/air-quality';

const getAirQualityMock = getAirQuality as jest.MockedFunction<typeof getAirQuality>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const badResponse: AirQualityResponse = {
  pm10: 92,
  pm25: 41,
  pm10Grade: 'BAD',
  pm25Grade: 'MODERATE',
  representativeGrade: 'BAD',
  stationName: '광진구',
  measuredAt: '2026-07-05T14:00:00Z',
};

const unknownResponse: AirQualityResponse = {
  pm10: null,
  pm25: null,
  pm10Grade: 'UNKNOWN',
  pm25Grade: 'UNKNOWN',
  representativeGrade: 'UNKNOWN',
  stationName: null,
  measuredAt: null,
};

describe('useAirQuality', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('U-01: 좌표가 주어지면 훅이 대기질 데이터를 반환한다', async () => {
    getAirQualityMock.mockResolvedValue(badResponse);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useAirQuality(37.4979, 127.0276), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.representativeGrade).toBe('BAD');
    expect(result.current.data?.pm10).toBe(92);
    expect(getAirQualityMock).toHaveBeenCalledWith(37.4979, 127.0276);

    unmount();
    queryClient.clear();
  });

  it('U-02: 좌표가 null이면 enabled=false로 조회하지 않는다', () => {
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useAirQuality(null, null), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(getAirQualityMock).not.toHaveBeenCalled();

    unmount();
    queryClient.clear();
  });

  it('U-03: API 실패 시 훅이 에러 상태를 노출한다', async () => {
    getAirQualityMock.mockRejectedValue(new Error('Internal Server Error'));
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useAirQuality(37.4979, 127.0276), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(Error);

    unmount();
    queryClient.clear();
  });

  it('U-04: BE UNKNOWN 응답을 success 데이터로 담는다(폴백은 컴포넌트 판단)', async () => {
    getAirQualityMock.mockResolvedValue(unknownResponse);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useAirQuality(0, 0), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.representativeGrade).toBe('UNKNOWN');
    expect(result.current.data?.pm10).toBeNull();

    unmount();
    queryClient.clear();
  });
});
