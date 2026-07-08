/**
 * be-client.ts — axios 인스턴스 (BE 직접 호출)
 *
 * Mobile은 BFF 없이 BE를 직접 호출합니다 (TDD 설계 결정 참조).
 * - accessToken: SecureStore에서 읽어 Authorization 헤더 자동 부착
 * - 401 응답: refreshToken으로 재발급 시도 → 실패 시 /auth/login으로 이동
 * - AsyncStorage 토큰 저장 금지 (expo-secure-store만 사용)
 */
import axios, {
  AxiosError,
  AxiosInstance,
  AxiosRequestConfig,
  InternalAxiosRequestConfig,
} from 'axios';
import { router } from 'expo-router';

import { getRefreshToken, useAuthStore } from '../lib/auth';
import { setItem as setSecureItem, deleteItem as deleteSecureItem } from '../lib/secure-store';

const REFRESH_TOKEN_KEY = 'refreshToken';

interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

/** 진행 중인 refresh 요청 (중복 방지) */
let refreshPromise: Promise<string> | null = null;

/**
 * refresh 실패 처리:
 * 1. SecureStore의 refreshToken 삭제
 * 2. 메모리 accessToken 삭제
 * 3. 로그인 화면으로 이동
 */
export async function handleRefreshFailure(): Promise<void> {
  await deleteSecureItem(REFRESH_TOKEN_KEY);
  useAuthStore.getState().setAccessToken(null);
  router.replace('/(auth)/login');
}

/**
 * axios 인스턴스를 생성합니다.
 * 테스트에서 baseURL을 주입하기 위한 팩토리 함수입니다.
 */
export function createBeClient(baseURL: string): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 15000,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  // Request 인터셉터 — Authorization 헤더 자동 부착
  instance.interceptors.request.use(
    async (config: InternalAxiosRequestConfig) => {
      const accessToken = useAuthStore.getState().accessToken;
      if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
      }
      return config;
    },
    (error: unknown) => Promise.reject(error)
  );

  // Response 인터셉터 — 401 처리
  instance.interceptors.response.use(
    (response) => response,
    async (error: unknown) => {
      if (!(error instanceof AxiosError)) {
        return Promise.reject(error);
      }

      const originalRequest = error.config as AxiosRequestConfig & {
        _retry?: boolean;
      };

      // 401이 아니거나 이미 재시도한 경우
      if (error.response?.status !== 401 || originalRequest._retry) {
        return Promise.reject(error);
      }

      originalRequest._retry = true;

      try {
        // 이미 진행 중인 refresh가 있으면 같은 Promise를 await — 동시 N개 401 처리 안전
        // refreshPromise 의 finally 안에서 단 한 번만 null 로 초기화 (재진입·동시성 보장)
        if (!refreshPromise) {
          refreshPromise = (async (): Promise<string> => {
            try {
              const storedRefreshToken = await getRefreshToken();
              if (!storedRefreshToken) {
                throw new Error('No refresh token available');
              }

              const response = await axios.post<RefreshResponse>(`${baseURL}/auth/refresh`, {
                refreshToken: storedRefreshToken,
              });

              const { accessToken, refreshToken: newRefreshToken } = response.data;

              // 새 토큰 저장
              useAuthStore.getState().setAccessToken(accessToken);
              await setSecureItem(REFRESH_TOKEN_KEY, newRefreshToken);

              return accessToken;
            } finally {
              refreshPromise = null;
            }
          })();
        }

        const newAccessToken = await refreshPromise;

        // 원 요청 재시도 — 기존 헤더 보존하면서 Authorization 만 갱신
        originalRequest.headers = {
          ...(originalRequest.headers ?? {}),
          Authorization: `Bearer ${newAccessToken}`,
        };

        return instance(originalRequest);
      } catch (refreshError) {
        await handleRefreshFailure();
        return Promise.reject(refreshError instanceof Error ? refreshError : error);
      }
    }
  );

  return instance;
}

/** Singleton 인스턴스 */
let _beClient: AxiosInstance | null = null;

/**
 * BE axios 인스턴스를 반환합니다.
 * EXPO_PUBLIC_API_URL이 설정되지 않으면 Error를 던집니다.
 */
export function getBeClient(): AxiosInstance {
  if (_beClient) {
    return _beClient;
  }

  const apiUrl = process.env.EXPO_PUBLIC_API_URL;
  if (!apiUrl) {
    throw new Error(
      'EXPO_PUBLIC_API_URL 환경변수가 설정되지 않았습니다. ' +
        '.env 파일에 EXPO_PUBLIC_API_URL을 설정하세요.'
    );
  }

  _beClient = createBeClient(apiUrl);
  return _beClient;
}

export default getBeClient;
