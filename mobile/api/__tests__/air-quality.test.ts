/**
 * U-01: GET /air-quality 호출 시 좌표 쿼리 파라미터와 함께 대기질 정보를 반환한다
 * U-02: BE가 실패 폴백(200 + UNKNOWN)을 반환해도 그대로 데이터로 전달한다
 * U-03: BE가 5xx를 반환하면 에러를 던진다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { AirQualityResponse } from '../types';
import { getAirQuality } from '../air-quality';

jest.mock('../be-client', () => {
  const actual = jest.requireActual('../be-client');
  return {
    ...actual,
    getBeClient: jest.fn(),
  };
});

import { getBeClient } from '../be-client';

const getBeClientMock = getBeClient as jest.MockedFunction<typeof getBeClient>;

describe('air-quality API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  beforeEach(() => {
    getBeClientMock.mockReturnValue(client);
  });

  afterEach(() => mock.reset());

  const successResponse: AirQualityResponse = {
    pm10: 92,
    pm25: 41,
    pm10Grade: 'BAD',
    pm25Grade: 'MODERATE',
    representativeGrade: 'BAD',
    stationName: '광진구',
    measuredAt: '2026-07-05T14:00:00Z',
  };

  describe('U-01: getAirQuality 성공', () => {
    it('lat/lng 쿼리 파라미터로 GET /air-quality를 호출해 대기질 정보를 반환한다', async () => {
      mock.onGet('/air-quality').reply(200, successResponse);

      const result = await getAirQuality(37.4979, 127.0276);

      expect(mock.history.get).toHaveLength(1);
      expect(mock.history.get[0].params).toEqual({ lat: 37.4979, lng: 127.0276 });
      expect(result.pm10).toBe(92);
      expect(result.representativeGrade).toBe('BAD');
      expect(result.stationName).toBe('광진구');
    });
  });

  describe('U-02: BE 실패 폴백(200 + UNKNOWN)', () => {
    it('BE가 UNKNOWN 등급과 null 수치를 반환하면 그대로 데이터로 담는다', async () => {
      const unknownResponse: AirQualityResponse = {
        pm10: null,
        pm25: null,
        pm10Grade: 'UNKNOWN',
        pm25Grade: 'UNKNOWN',
        representativeGrade: 'UNKNOWN',
        stationName: null,
        measuredAt: null,
      };
      mock.onGet('/air-quality').reply(200, unknownResponse);

      const result = await getAirQuality(0, 0);

      expect(result.representativeGrade).toBe('UNKNOWN');
      expect(result.pm10).toBeNull();
      expect(result.stationName).toBeNull();
    });
  });

  describe('U-03: BE 5xx 오류', () => {
    it('서버 오류(500) 응답 시 에러를 던진다', async () => {
      mock.onGet('/air-quality').reply(500, { message: 'Internal Server Error' });

      await expect(getAirQuality(37.4979, 127.0276)).rejects.toThrow();
    });
  });
});
