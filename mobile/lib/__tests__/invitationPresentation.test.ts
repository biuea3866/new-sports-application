/**
 * invitationPresentation — S6/S7 초대 화면의 순수 표시·판정 로직 검증.
 * 근거: design-fe-app.md S6·S7 와이어프레임, tickets/FE-13 테스트 케이스.
 */
import {
  formatExpiryDDay,
  formatSpeakPermissionLabel,
  isReusedPendingInvitation,
} from '../invitationPresentation';

describe('formatSpeakPermissionLabel', () => {
  it('canSpeak=true면 "발화 가능"을 반환한다', () => {
    expect(formatSpeakPermissionLabel(true)).toBe('발화 가능');
  });

  it('canSpeak=false면 "읽기 전용"을 반환한다', () => {
    expect(formatSpeakPermissionLabel(false)).toBe('읽기 전용');
  });
});

describe('formatExpiryDDay', () => {
  const now = new Date('2026-07-06T00:00:00+09:00');

  it('7일 뒤 만료면 "D-7"을 반환한다', () => {
    const expiresAt = new Date('2026-07-13T00:00:00+09:00').toISOString();
    expect(formatExpiryDDay(expiresAt, now)).toBe('D-7');
  });

  it('당일 만료면 "D-DAY"를 반환한다', () => {
    const expiresAt = new Date('2026-07-06T10:00:00+09:00').toISOString();
    expect(formatExpiryDDay(expiresAt, now)).toBe('D-DAY');
  });

  it('이미 만료 시각이 지났으면 "만료됨"을 반환한다', () => {
    const expiresAt = new Date('2026-07-01T00:00:00+09:00').toISOString();
    expect(formatExpiryDDay(expiresAt, now)).toBe('만료됨');
  });
});

describe('isReusedPendingInvitation', () => {
  it('생성 시각이 제출 시각 직전(3초 이내)이면 신규 생성으로 판단해 false를 반환한다', () => {
    const submittedAt = new Date('2026-07-06T00:00:03+09:00');
    const createdAt = new Date('2026-07-06T00:00:01+09:00').toISOString();

    expect(isReusedPendingInvitation(createdAt, submittedAt)).toBe(false);
  });

  it('생성 시각이 제출 시각보다 3초 넘게 이전이면 기존 초대 재사용으로 판단해 true를 반환한다', () => {
    const submittedAt = new Date('2026-07-06T00:05:00+09:00');
    const createdAt = new Date('2026-07-05T00:00:00+09:00').toISOString();

    expect(isReusedPendingInvitation(createdAt, submittedAt)).toBe(true);
  });
});
