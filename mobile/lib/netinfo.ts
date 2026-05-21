/**
 * netinfo.ts — 네트워크 상태 훅 래퍼
 *
 * @react-native-community/netinfo의 useNetInfo를 그대로 재노출합니다.
 * 컴포넌트에서 직접 라이브러리를 import하지 않고 이 모듈을 사용합니다.
 * 테스트 시 이 모듈만 mock하면 됩니다.
 */
import { useNetInfo as useRNNetInfo } from '@react-native-community/netinfo';
import type { NetInfoState } from '@react-native-community/netinfo';

export type { NetInfoState };

/**
 * 현재 네트워크 연결 상태를 반환하는 훅입니다.
 * isConnected가 null이면 아직 상태를 알 수 없는 초기 상태입니다.
 */
export function useNetInfo(): NetInfoState {
  return useRNNetInfo();
}
