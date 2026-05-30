/**
 * auth.ts — 토큰 관리
 *
 * - accessToken: Zustand 메모리 (재기동 시 소멸, 보안)
 * - refreshToken: expo-secure-store (앱 재기동 시 유지)
 * - AsyncStorage 토큰 저장 절대 금지
 */
import { create } from 'zustand';
import { getItem, setItem, deleteItem } from './secure-store';

const REFRESH_TOKEN_KEY = 'refreshToken';

interface SetTokensParams {
  accessToken: string;
  refreshToken: string;
}

interface AuthState {
  accessToken: string | null;
  isAuthenticated: () => boolean;
  setTokens: (params: SetTokensParams) => Promise<void>;
  setAccessToken: (token: string | null) => void;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,

  isAuthenticated: () => get().accessToken !== null,

  setTokens: async ({ accessToken, refreshToken }: SetTokensParams) => {
    // refreshToken은 SecureStore에 (디스크 암호화 영역)
    await setItem(REFRESH_TOKEN_KEY, refreshToken);
    // accessToken은 메모리에만
    set({ accessToken });
  },

  setAccessToken: (token: string | null) => {
    set({ accessToken: token });
  },

  logout: async () => {
    // SecureStore에서 refreshToken 삭제
    await deleteItem(REFRESH_TOKEN_KEY);
    // 메모리에서 accessToken 삭제
    set({ accessToken: null });
  },
}));

/**
 * SecureStore에서 refreshToken을 읽어옵니다.
 * 메모리에는 저장하지 않습니다.
 */
export async function getRefreshToken(): Promise<string | null> {
  return getItem(REFRESH_TOKEN_KEY);
}

/**
 * SecureStore에서 refreshToken을 저장합니다.
 * 직접 호출보다 useAuthStore.setTokens() 사용을 권장합니다.
 */
export async function saveRefreshToken(token: string): Promise<void> {
  await setItem(REFRESH_TOKEN_KEY, token);
}

/**
 * SecureStore에서 refreshToken을 삭제합니다.
 * 직접 호출보다 useAuthStore.logout() 사용을 권장합니다.
 */
export async function clearRefreshToken(): Promise<void> {
  await deleteItem(REFRESH_TOKEN_KEY);
}
