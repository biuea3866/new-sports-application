/**
 * lib/entryTokenStore.ts — 가상 대기열 입장 토큰 스토어
 *
 * 근거: 티켓 FE-02, `20260709-가상대기열-design-fe-app.md` "상태관리 설계".
 * 대기실 → 구매 API 경계를 넘길 휘발성 입장 토큰(HMAC 문자열)을 target(`{type}:{targetId}`)별로
 * 보관한다. `lib/auth.ts#useAuthStore`(accessToken 메모리 보관) 선례와 동형 —
 * 서버 표시 캐시가 아니라 화면 경계를 넘는 자격증명 전달용이라 전역(Zustand) 승격이 정당하다.
 *
 * - 앱 메모리에만 보관한다(디스크 저장 금지, 재기동 시 소멸).
 * - `useEntryTokenStore.getState().tokenFor(type, targetId)`로 React 밖(api 함수)에서도
 *   조회할 수 있다 (`api/be-client.ts#getBeClient`가 `useAuthStore.getState()`를 쓰는 선례와 동형).
 * - 만료 시각(`expiresAt`)이 지난 토큰은 조회 시 null을 반환한다.
 */
import { create } from 'zustand';

/** 대기열 대상 유형 — design-fe-app.md "Terminology" Target(대상) */
export type QueueTargetType = 'limited-drop' | 'ticketing-event';

interface EntryTokenEntry {
  token: string;
  /** ISO-8601 만료 시각 */
  expiresAt: string;
}

interface EntryTokenState {
  tokens: Record<string, EntryTokenEntry>;
  setToken: (type: QueueTargetType, targetId: number, token: string, expiresAt: string) => void;
  tokenFor: (type: QueueTargetType, targetId: number) => string | null;
  clear: (type: QueueTargetType, targetId: number) => void;
}

function keyFor(type: QueueTargetType, targetId: number): string {
  return `${type}:${targetId}`;
}

function isExpired(expiresAt: string): boolean {
  return new Date(expiresAt).getTime() <= Date.now();
}

export const useEntryTokenStore = create<EntryTokenState>((set, get) => ({
  tokens: {},

  setToken: (type, targetId, token, expiresAt) => {
    const key = keyFor(type, targetId);
    set((state) => ({
      tokens: { ...state.tokens, [key]: { token, expiresAt } },
    }));
  },

  tokenFor: (type, targetId) => {
    const key = keyFor(type, targetId);
    const entry = get().tokens[key];
    if (!entry) {
      return null;
    }
    if (isExpired(entry.expiresAt)) {
      return null;
    }
    return entry.token;
  },

  clear: (type, targetId) => {
    const key = keyFor(type, targetId);
    set((state) => {
      const nextTokens = { ...state.tokens };
      delete nextTokens[key];
      return { tokens: nextTokens };
    });
  },
}));
