/**
 * external-features.ts — Geocoding/Weather/Push 등 "외부 API 기반" 기능의 BE 호출 래퍼.
 *
 * 중요: 프론트는 외부 Open API(Kakao/기상청/Expo 등)를 직접 호출하지 않습니다.
 * 반드시 우리 backend WAS 엔드포인트만 호출하고, 외부 호출은 backend Gateway 가 담당합니다.
 * (rules/fe-external-api-via-was.md)
 */
import { getBeClient } from './be-client';

export interface FacilitySummary {
  id: string;
  name: string;
  gu: string;
  type: string;
  address: string;
  lat: number;
  lng: number;
  parking: boolean;
  tel: string;
  homePage: string;
  eduYn: boolean;
}

/** 내 주변 시설 — BE 가 좌표 기반 조회(GeoSpatial). 외부 지도 API 직접 호출 아님. */
export async function getNearbyFacilities(
  lat: number,
  lng: number,
  radiusMeters = 2000
): Promise<FacilitySummary[]> {
  const { data } = await getBeClient().get<FacilitySummary[]>('/facilities/near', {
    params: { lat, lng, radiusMeters },
  });
  return data;
}

export interface ForecastSlot {
  date: string;
  time: string;
  temperature: number | null;
  sky: string | null;
  precipitationType: string | null;
  precipitationProbability: number | null;
  humidity: number | null;
  windSpeed: number | null;
}

export interface Forecast {
  slots: ForecastSlot[];
}

/** 날씨 — BE 가 기상청 단기예보를 조회. 프론트는 기상청을 직접 호출하지 않음. */
export async function getWeather(lat: number, lng: number): Promise<Forecast> {
  const { data } = await getBeClient().get<Forecast>('/weather', {
    params: { lat, lng },
  });
  return data;
}

export type PushPlatform = 'IOS' | 'ANDROID' | 'WEB';

export interface PushTokenResponse {
  id: number;
  platform: string;
}

/**
 * 푸시 토큰 등록 — 기기는 expo SDK 로 자기 토큰만 얻어 우리 BE 에 등록한다.
 * 실제 푸시 발송은 backend 의 PushChannelGateway 가 Expo 로 수행한다(프론트가 exp.host 직접 호출 금지).
 */
export async function registerPushToken(
  userId: number,
  token: string,
  platform: PushPlatform
): Promise<PushTokenResponse> {
  const { data } = await getBeClient().post<PushTokenResponse>(
    '/notifications/push-tokens',
    { token, platform },
    { headers: { 'X-User-Id': String(userId) } }
  );
  return data;
}
