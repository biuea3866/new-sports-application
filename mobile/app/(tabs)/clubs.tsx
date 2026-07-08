/**
 * "동아리" 탭 진입점 (FE-15)
 *
 * 실제 커뮤니티(동아리) 목록 UI는 루트 Stack에 등록된 `communities/index`(FE-11)가
 * 담당한다. 탭 이름을 `communities`가 아닌 `clubs`로 둔 이유: `(tabs)/clubs.tsx`가
 * `/clubs` 경로를 차지해야 최상위 `communities/index.tsx`의 `/communities` 경로와
 * 충돌하지 않는다. 탭 프레스는 `(tabs)/_layout.tsx`의 `listeners.tabPress`가 가로채
 * 화면 전환 없이 `/communities`로 이동시킨다. 이 파일은 딥링크 등으로 `/clubs` 경로에
 * 직접 진입했을 때의 폴백으로만 리다이렉트를 수행한다.
 *
 * `chat.community.enabled` OFF 시 탭 자체가 `_layout.tsx`에서 `href: null`로 숨겨진다.
 */
import { useEffect } from 'react';
import { useRouter } from 'expo-router';

export default function ClubsTabEntryScreen() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/communities');
  }, [router]);

  return null;
}
