/**
 * invitationPresentation — S6(초대 발송)·S7(초대 수신함) 화면의 순수 표시·판정 로직.
 *
 * 근거: design-fe-app.md S6·S7 와이어프레임, tickets/FE-13.
 * 계산·판단 로직을 컴포넌트 밖 순수 함수로 분리한다(no-logic-in-component).
 *
 * BE는 초대 발송 멱등 처리 시 신규 생성 대신 기존 PENDING 초대를 그대로 응답한다
 * (`invitation.ts` 주석 참조). InvitationResponse에는 "재사용 여부"를 나타내는 별도
 * 필드가 없으므로, 응답의 createdAt이 제출 시각보다 REUSED_THRESHOLD_MS 이상 과거이면
 * 기존 초대가 재사용된 것으로 판단한다.
 */
const MILLISECONDS_PER_DAY = 1000 * 60 * 60 * 24;
const REUSED_THRESHOLD_MILLISECONDS = 3000;

export function formatSpeakPermissionLabel(canSpeak: boolean): string {
  return canSpeak ? '발화 가능' : '읽기 전용';
}

export function formatExpiryDDay(expiresAt: string, now: Date = new Date()): string {
  const expiryDate = new Date(expiresAt);
  const diffMilliseconds = expiryDate.getTime() - now.getTime();

  if (diffMilliseconds < 0) {
    return '만료됨';
  }

  const diffDays = Math.floor(diffMilliseconds / MILLISECONDS_PER_DAY);
  if (diffDays <= 0) {
    return 'D-DAY';
  }
  return `D-${diffDays}`;
}

export function isReusedPendingInvitation(createdAt: string, submittedAt: Date): boolean {
  const createdAtMilliseconds = new Date(createdAt).getTime();
  const elapsedMilliseconds = submittedAt.getTime() - createdAtMilliseconds;
  return elapsedMilliseconds > REUSED_THRESHOLD_MILLISECONDS;
}
