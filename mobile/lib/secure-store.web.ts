/**
 * secure-store.web.ts — 웹 전용 보안 스토리지 fallback
 *
 * expo-secure-store는 네이티브 전용(Keychain/Keystore)이라 웹에 구현이 없다.
 * 웹 데모 실행을 위해 localStorage로 대체한다.
 * 주의: localStorage는 암호화되지 않으므로 웹은 데모/개발 용도에 한정한다.
 */
export async function getItem(key: string): Promise<string | null> {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(key);
}

export async function setItem(key: string, value: string): Promise<void> {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(key, value);
}

export async function deleteItem(key: string): Promise<void> {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(key);
}
