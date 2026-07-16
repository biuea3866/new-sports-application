/**
 * 탭 라우트 파일 구성 회귀 가드.
 *
 * 사용자 피드백으로 탭을 7개→5개로 재편하면서(채팅·동아리·티켓 탭 제거, 스토어에 티켓,
 * 커뮤니티에 동아리 세그먼트 통합, search→facilities 재명명) 예전 실수를 다시 만들지
 * 않도록 파일 구성 자체를 검증한다.
 *
 * - 예전에는 `(tabs)/community.tsx`와 루트 `app/community/index.tsx`가 `/community`
 *   경로를 두고 충돌해 탭 화면 대신 "준비중" 플레이스홀더가 뜨는 버그가 있었다.
 * - 예전에는 채팅·동아리 탭이 `tabPress`를 가로채 루트 스택(`/rooms`, `/communities`)으로
 *   `router.replace`해 탭바가 사라지는 버그가 있었다. 이제 채팅·동아리는 탭 자체가
 *   없고(스토어·커뮤니티 탭 내부 세그먼트 또는 `ChatEntryButton` 진입점으로 대체),
 *   해당 탭 파일(chat.tsx/clubs.tsx/tickets.tsx)도 삭제됐다 — 재도입 시 이 가드가 잡는다.
 */
import { existsSync } from 'fs';
import { join } from 'path';

const TABS_DIR = join(__dirname, '..');

describe('탭 라우트 파일 구성', () => {
  it('루트 community/index 플레이스홀더가 (tabs)/community 경로를 가리지 않는다', () => {
    const placeholder = join(TABS_DIR, '..', 'community', 'index.tsx');

    expect(existsSync(placeholder)).toBe(false);
  });

  it('탭 재편으로 제거된 chat/clubs/tickets 탭 파일이 재도입되지 않았다', () => {
    expect(existsSync(join(TABS_DIR, 'chat.tsx'))).toBe(false);
    expect(existsSync(join(TABS_DIR, 'clubs.tsx'))).toBe(false);
    expect(existsSync(join(TABS_DIR, 'tickets.tsx'))).toBe(false);
  });

  it('모호한 이름의 search 탭 파일이 남아있지 않고 facilities로 대체됐다', () => {
    expect(existsSync(join(TABS_DIR, 'search.tsx'))).toBe(false);
    expect(existsSync(join(TABS_DIR, 'facilities.tsx'))).toBe(true);
  });

  it('스토어·커뮤니티 탭 파일이 존재한다(굿즈|티켓, 게시글|동아리 통합 화면)', () => {
    expect(existsSync(join(TABS_DIR, 'store.tsx'))).toBe(true);
    expect(existsSync(join(TABS_DIR, 'community.tsx'))).toBe(true);
  });
});
