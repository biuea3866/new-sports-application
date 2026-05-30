/**
 * secure-store.ts — 토큰 보관용 보안 스토리지 래퍼 (네이티브)
 *
 * 네이티브(iOS/Android)에서는 expo-secure-store(Keychain/Keystore)에 위임한다.
 * 웹에서는 secure-store.web.ts가 Metro 플랫폼 해석으로 대체된다.
 */
import * as SecureStore from 'expo-secure-store';

export function getItem(key: string): Promise<string | null> {
  return SecureStore.getItemAsync(key);
}

export function setItem(key: string, value: string): Promise<void> {
  return SecureStore.setItemAsync(key, value);
}

export function deleteItem(key: string): Promise<void> {
  return SecureStore.deleteItemAsync(key);
}
