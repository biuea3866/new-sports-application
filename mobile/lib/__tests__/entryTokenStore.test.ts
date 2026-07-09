/**
 * lib/entryTokenStore.ts — 입장 토큰 스토어 검증
 * 근거: 티켓 FE-02, design-fe-app.md "상태관리 설계" (입장 토큰 — 전역·휘발, useAuthStore 선례와 동형)
 */
import { useEntryTokenStore } from '../entryTokenStore';

describe('useEntryTokenStore', () => {
  beforeEach(() => {
    useEntryTokenStore.setState({ tokens: {} });
  });

  const futureTimestamp = () => new Date(Date.now() + 60_000).toISOString();

  it('target별로 토큰을 저장하고 동일 target 조회 시 반환한다', () => {
    useEntryTokenStore
      .getState()
      .setToken('limited-drop', 42, 'entry-token-abc', futureTimestamp());

    const token = useEntryTokenStore.getState().tokenFor('limited-drop', 42);

    expect(token).toBe('entry-token-abc');
  });

  it('다른 target의 토큰은 서로 격리되어 조회되지 않는다', () => {
    useEntryTokenStore.getState().setToken('limited-drop', 42, 'token-for-42', futureTimestamp());

    const tokenForOtherId = useEntryTokenStore.getState().tokenFor('limited-drop', 99);
    const tokenForOtherType = useEntryTokenStore.getState().tokenFor('ticketing-event', 42);

    expect(tokenForOtherId).toBeNull();
    expect(tokenForOtherType).toBeNull();
  });

  it('expiresAt이 지난 토큰은 tokenFor가 null을 반환한다', () => {
    const pastTimestamp = new Date(Date.now() - 60_000).toISOString();
    useEntryTokenStore.getState().setToken('ticketing-event', 7, 'expired-token', pastTimestamp);

    const token = useEntryTokenStore.getState().tokenFor('ticketing-event', 7);

    expect(token).toBeNull();
  });

  it('clear 후 조회 시 null을 반환한다', () => {
    useEntryTokenStore.getState().setToken('limited-drop', 1, 'to-be-cleared', futureTimestamp());

    useEntryTokenStore.getState().clear('limited-drop', 1);

    expect(useEntryTokenStore.getState().tokenFor('limited-drop', 1)).toBeNull();
  });

  it('만료되지 않은 토큰은 tokenFor가 null을 반환하지 않는다(경계값 — 미래 만료)', () => {
    useEntryTokenStore.getState().setToken('limited-drop', 5, 'still-valid', futureTimestamp());

    expect(useEntryTokenStore.getState().tokenFor('limited-drop', 5)).toBe('still-valid');
  });
});
