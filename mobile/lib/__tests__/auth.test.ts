/**
 * R-01: accessToken은 메모리(Zustand)에, refreshToken은 SecureStore에 저장된다
 * R-02: logout 시 SecureStore + 메모리 비움
 */
import * as SecureStore from 'expo-secure-store';
import { useAuthStore, getRefreshToken, saveRefreshToken, clearRefreshToken } from '../auth';

jest.mock('expo-secure-store');
jest.mock('expo-router', () => ({
  router: { replace: jest.fn() },
}));

const mockedSecureStore = jest.mocked(SecureStore);

describe('useAuthStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Zustand 스토어 리셋
    useAuthStore.setState({ accessToken: null });
  });

  describe('R-01: 토큰 저장 위치', () => {
    it('setTokens 호출 시 accessToken은 store 메모리에, refreshToken은 SecureStore에 저장된다', async () => {
      mockedSecureStore.setItemAsync.mockResolvedValue(undefined);

      await useAuthStore.getState().setTokens({
        accessToken: 'access-abc',
        refreshToken: 'refresh-xyz',
      });

      // accessToken은 메모리에
      const state = useAuthStore.getState();
      expect(state.accessToken).toBe('access-abc');

      // refreshToken은 SecureStore에
      expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith('refreshToken', 'refresh-xyz');

      // refreshToken은 메모리(store 상태 객체)에 없어야 한다
      const stateKeys = Object.keys(state);
      expect(stateKeys).not.toContain('refreshToken');
    });

    it('getRefreshToken은 SecureStore에서 읽어온다', async () => {
      mockedSecureStore.getItemAsync.mockResolvedValue('stored-refresh');

      const token = await getRefreshToken();

      expect(token).toBe('stored-refresh');
      expect(mockedSecureStore.getItemAsync).toHaveBeenCalledWith('refreshToken');
    });
  });

  describe('R-02: logout 시 토큰 초기화', () => {
    it('logout 호출 시 메모리 accessToken이 null로 초기화된다', async () => {
      mockedSecureStore.setItemAsync.mockResolvedValue(undefined);
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);

      // 먼저 토큰 세팅
      await useAuthStore.getState().setTokens({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
      });

      expect(useAuthStore.getState().accessToken).toBe('access-token');

      // logout 실행
      await useAuthStore.getState().logout();

      // 메모리 초기화 확인
      expect(useAuthStore.getState().accessToken).toBeNull();
    });

    it('logout 호출 시 SecureStore의 refreshToken이 삭제된다', async () => {
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);

      await useAuthStore.getState().logout();

      expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
    });

    it('logout 후 isAuthenticated가 false이다', async () => {
      mockedSecureStore.setItemAsync.mockResolvedValue(undefined);
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);

      await useAuthStore.getState().setTokens({
        accessToken: 'token',
        refreshToken: 'refresh',
      });

      expect(useAuthStore.getState().isAuthenticated()).toBe(true);

      await useAuthStore.getState().logout();

      expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    });
  });

  describe('헬퍼 함수', () => {
    it('saveRefreshToken은 SecureStore에 저장한다', async () => {
      mockedSecureStore.setItemAsync.mockResolvedValue(undefined);

      await saveRefreshToken('my-refresh-token');

      expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith(
        'refreshToken',
        'my-refresh-token'
      );
    });

    it('clearRefreshToken은 SecureStore에서 삭제한다', async () => {
      mockedSecureStore.deleteItemAsync.mockResolvedValue(undefined);

      await clearRefreshToken();

      expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
    });
  });
});
