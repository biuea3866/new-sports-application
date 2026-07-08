/**
 * "채팅" 탭 진입점 (FE-15)
 *
 * 실제 채팅방 목록 UI는 루트 Stack에 등록된 `rooms/index`(FE-09)가 담당한다.
 * 탭 프레스는 `(tabs)/_layout.tsx`의 `listeners.tabPress`가 가로채 화면 전환 없이
 * `/rooms`로 바로 이동시킨다. 이 파일은 딥링크 등으로 `/chat` 경로에 직접 진입했을 때의
 * 폴백으로만 리다이렉트를 수행한다.
 */
import { useEffect } from 'react';
import { useRouter } from 'expo-router';

export default function ChatTabEntryScreen() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/rooms');
  }, [router]);

  return null;
}
