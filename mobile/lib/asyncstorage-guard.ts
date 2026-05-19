/**
 * asyncstorage-guard.ts — AsyncStorage 토큰 저장 차단 런타임 가드
 *
 * AsyncStorage는 디스크에 평문 저장되므로 토큰 보관에 사용 금지.
 * 토큰은 expo-secure-store (iOS Keychain / Android Keystore)를 사용.
 *
 * 이 모듈은 두 가지 보호 레이어를 제공합니다:
 * 1. 정적 린트 규칙 (.eslintrc — no-restricted-imports)
 * 2. 런타임 가드 (guardedAsyncStorage)
 */

/** 토큰 저장에 사용하면 안 되는 키 패턴 */
const TOKEN_KEY_PATTERNS: RegExp[] = [
  /^access[-_]?token$/i,
  /^refresh[-_]?token$/i,
  /^auth[-_]?token$/i,
  /^jwt$/i,
  /^token$/i,
  /token/i,
  /^bearer/i,
];

function isTokenKey(key: string): boolean {
  return TOKEN_KEY_PATTERNS.some((pattern) => pattern.test(key));
}

/**
 * AsyncStorage 래퍼 — 토큰 관련 키 접근 시 차단.
 *
 * @example
 * // 금지: 토큰을 AsyncStorage에 저장
 * await guardedAsyncStorage.setItem('accessToken', token); // throws Error
 *
 * // 허용: 토큰이 아닌 일반 데이터
 * await guardedAsyncStorage.setItem('user-preferences', data); // OK
 */
export const guardedAsyncStorage = {
  async setItem(key: string, _value: string): Promise<void> {
    if (isTokenKey(key)) {
      throw new Error(
        `AsyncStorage에 토큰 저장 금지: key="${key}". ` +
          'expo-secure-store를 사용하세요 (lib/auth.ts 참조).'
      );
    }
    // 실제 프로덕션에서는 여기서 AsyncStorage.setItem을 호출합니다.
    // 본 프로젝트에서 비토큰 데이터는 별도 필요 시 구현합니다.
    await Promise.resolve();
  },

  async getItem(key: string): Promise<string | null> {
    if (isTokenKey(key)) {
      console.warn(
        `AsyncStorage에서 토큰 읽기 시도 차단: key="${key}". ` +
          'expo-secure-store를 사용하세요 (lib/auth.ts 참조).'
      );
      return null;
    }
    await Promise.resolve();
    return null;
  },

  async removeItem(key: string): Promise<void> {
    if (isTokenKey(key)) {
      console.warn(
        `AsyncStorage에서 토큰 삭제 시도 차단: key="${key}". ` +
          'expo-secure-store를 사용하세요 (lib/auth.ts 참조).'
      );
      return;
    }
    await Promise.resolve();
  },
};
