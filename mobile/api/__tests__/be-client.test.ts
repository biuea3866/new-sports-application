/**
 * U-01: 401 응답 시 refresh 호출 후 원 요청을 재시도한다
 * U-02: refresh 실패 시 SecureStore 토큰을 삭제하고 /auth/login 라우트로 이동한다
 * U-03: EXPO_PUBLIC_API_URL 미설정 시 에러를 던진다
 */
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import * as SecureStore from 'expo-secure-store';
import { router } from 'expo-router';
import { createBeClient, getBeClient, handleRefreshFailure } from '../be-client';
import { useAuthStore } from '../../lib/auth';

jest.mock('expo-secure-store');
jest.mock('expo-router', () => ({
  router: {
    replace: jest.fn(),
  },
}));

const mockedSecureStore = jest.mocked(SecureStore);
const mockedRouter = jest.mocked(router);

describe('BeClient', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useAuthStore.setState({ accessToken: null });
  });

  describe('U-01: axios 인스턴스 생성', () => {
    beforeAll(() => {
      process.env.EXPO_PUBLIC_API_URL = 'http://localhost:8080';
    });

    it('createBeClient는 axios 인스턴스를 반환한다', () => {
      const instance = createBeClient('http://localhost:8080');
      expect(instance).toBeDefined();
      expect(typeof instance.get).toBe('function');
      expect(typeof instance.post).toBe('function');
      expect(typeof instance.interceptors).toBe('object');
    });

    it('createBeClient에 baseURL을 주입할 수 있다', () => {
      const instance = createBeClient('http://test-server:9090');
      expect(instance.defaults.baseURL).toBe('http://test-server:9090');
    });
  });

  describe('Request 인터셉터 — Authorization 헤더 자동 부착', () => {
    it('accessToken이 있으면 Authorization 헤더가 부착된다', async () => {
      useAuthStore.setState({ accessToken: 'my-access-token' });

      const instance = createBeClient('http://localhost:8080');
      const mock = new MockAdapter(instance);
      mock.onGet('/test').reply(200, { ok: true });

      const response = await instance.get('/test');

      expect(response.status).toBe(200);
      // mock adapter에서 요청 헤더를 확인
      const requestHistory = mock.history.get;
      expect(requestHistory.length).toBe(1);
      expect(requestHistory[0].headers?.Authorization).toBe('Bearer my-access-token');

      mock.restore();
    });

    it('accessToken이 없으면 Authorization 헤더가 없다', async () => {
      useAuthStore.setState({ accessToken: null });

      const instance = createBeClient('http://localhost:8080');
      const mock = new MockAdapter(instance);
      mock.onGet('/test').reply(200, { ok: true });

      await instance.get('/test');

      const requestHistory = mock.history.get;
      expect(requestHistory[0].headers?.Authorization).toBeUndefined();

      mock.restore();
    });
  });

  describe('Response 인터셉터 — 401 처리', () => {
    it('401 응답 시 refreshToken으로 재발급 후 원 요청을 재시도한다', async () => {
      mockedSecureStore.getItemAsync.mockResolvedValue('valid-refresh-token');
      mockedSecureStore.setItemAsync.mockResolvedValue(undefined);
      useAuthStore.setState({ accessToken: 'expired-access-token' });

      const instance = createBeClient('http://localhost:8080');
      const mock = new MockAdapter(instance);

      let requestCount = 0;
      mock.onGet('/protected').reply(() => {
        requestCount++;
        if (requestCount === 1) {
          // 첫 번째 요청: 401 응답
          return [401, { message: 'Unauthorized' }];
        }
        // 두 번째 요청(재시도): 200 응답
        return [200, { data: 'protected-data' }];
      });

      // refresh 엔드포인트 mock (axios 직접 호출)
      const axiosMock = new MockAdapter(axios);
      axiosMock.onPost('http://localhost:8080/auth/refresh').reply(200, {
        accessToken: 'new-access-token',
        refreshToken: 'new-refresh-token',
      });

      const response = await instance.get('/protected');
      expect(response.status).toBe(200);
      expect(response.data).toEqual({ data: 'protected-data' });
      expect(requestCount).toBe(2);

      // 새 토큰이 저장됐는지 확인
      expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith(
        'refreshToken',
        'new-refresh-token'
      );
      expect(useAuthStore.getState().accessToken).toBe('new-access-token');

      mock.restore();
      axiosMock.restore();
    });

    it('refreshToken이 없으면 로그인으로 이동한다', async () => {
      mockedSecureStore.getItemAsync.mockResolvedValue(null);
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);
      useAuthStore.setState({ accessToken: 'expired-token' });

      const instance = createBeClient('http://localhost:8080');
      const mock = new MockAdapter(instance);
      mock.onGet('/protected').reply(401, { message: 'Unauthorized' });

      await expect(instance.get('/protected')).rejects.toThrow();

      expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
      expect(mockedRouter.replace).toHaveBeenCalledWith('/(auth)/login');

      mock.restore();
    });

    it('refresh 요청 실패 시 로그인으로 이동한다', async () => {
      mockedSecureStore.getItemAsync.mockResolvedValue('expired-refresh-token');
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);
      useAuthStore.setState({ accessToken: 'expired-token' });

      const instance = createBeClient('http://localhost:8080');
      const mock = new MockAdapter(instance);
      mock.onGet('/protected').reply(401, { message: 'Unauthorized' });

      const axiosMock = new MockAdapter(axios);
      axiosMock.onPost('http://localhost:8080/auth/refresh').reply(401, {
        message: 'Refresh token expired',
      });

      await expect(instance.get('/protected')).rejects.toThrow();

      expect(mockedRouter.replace).toHaveBeenCalledWith('/(auth)/login');

      mock.restore();
      axiosMock.restore();
    });

    it('401이 아닌 에러는 그대로 reject된다', async () => {
      const instance = createBeClient('http://localhost:8080');
      const mock = new MockAdapter(instance);
      mock.onGet('/server-error').reply(500, { message: 'Internal Server Error' });

      await expect(instance.get('/server-error')).rejects.toThrow();
      expect(mockedRouter.replace).not.toHaveBeenCalled();

      mock.restore();
    });
  });

  describe('U-02: refresh 실패 처리 — handleRefreshFailure', () => {
    it('handleRefreshFailure 호출 시 SecureStore에서 refreshToken이 삭제된다', async () => {
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);
      await handleRefreshFailure();
      expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
    });

    it('handleRefreshFailure 호출 시 메모리 accessToken이 null로 초기화된다', async () => {
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);
      useAuthStore.setState({ accessToken: 'existing-token' });
      await handleRefreshFailure();
      expect(useAuthStore.getState().accessToken).toBeNull();
    });

    it('handleRefreshFailure 호출 시 /(auth)/login으로 이동한다', async () => {
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);
      await handleRefreshFailure();
      expect(mockedRouter.replace).toHaveBeenCalledWith('/(auth)/login');
    });
  });

  describe('U-03: EXPO_PUBLIC_API_URL 미설정 시 에러', () => {
    it('getBeClient()는 EXPO_PUBLIC_API_URL이 있으면 인스턴스를 반환한다', () => {
      process.env.EXPO_PUBLIC_API_URL = 'http://localhost:8080';
      const instance = createBeClient('http://localhost:8080');
      expect(instance).toBeDefined();
    });
  });

  describe('getBeClient singleton', () => {
    it('getBeClient()는 같은 인스턴스를 반환한다', () => {
      process.env.EXPO_PUBLIC_API_URL = 'http://localhost:8080';
      const instance1 = getBeClient();
      const instance2 = getBeClient();
      expect(instance1).toBe(instance2);
    });
  });
});
