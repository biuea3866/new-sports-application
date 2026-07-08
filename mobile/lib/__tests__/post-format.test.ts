/**
 * post-format — 상대 시간·작성자 라벨 순수 포맷 유틸 검증.
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-P1/A-P2("홍길동 · 2일 전").
 */
import { formatAuthor, formatRelativeTime } from '../post-format';

describe('formatRelativeTime', () => {
  const now = new Date('2026-07-08T12:00:00+09:00');

  it('1분 미만이면 방금 전을 반환한다', () => {
    expect(formatRelativeTime('2026-07-08T11:59:30+09:00', now)).toBe('방금 전');
  });

  it('1시간 미만이면 N분 전을 반환한다', () => {
    expect(formatRelativeTime('2026-07-08T11:30:00+09:00', now)).toBe('30분 전');
  });

  it('하루 미만이면 N시간 전을 반환한다', () => {
    expect(formatRelativeTime('2026-07-08T09:00:00+09:00', now)).toBe('3시간 전');
  });

  it('30일 미만이면 N일 전을 반환한다', () => {
    expect(formatRelativeTime('2026-07-06T12:00:00+09:00', now)).toBe('2일 전');
  });

  it('30일 이상이면 절대 날짜를 반환한다', () => {
    expect(formatRelativeTime('2026-05-01T12:00:00+09:00', now)).toBe('2026.05.01');
  });
});

describe('formatAuthor', () => {
  it('사용자 ID를 작성자 라벨로 변환한다', () => {
    expect(formatAuthor(42)).toBe('사용자 42');
  });
});
