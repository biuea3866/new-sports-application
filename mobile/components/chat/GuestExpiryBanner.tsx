/**
 * GuestExpiryBanner — 본인 게스트 참여 만료 D-day 안내 배너.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` S2 와이어프레임
 * "⏳ 게스트 참여 만료: D-2 — (조건, 본인 게스트) 만료 안내 배너".
 *
 * 확인 필요(미해결): BE REST 계약(`20260704-채팅시스템고도화-tdd.md`)에는 현재 로그인 사용자의
 * 방 참여자 정보(`RoomParticipant.canSpeak`/`expiresAt`)를 조회하는 엔드포인트가 없다
 * (`InvitationResponse.expiresAt`은 "초대"의 만료 시각이지 "참여자" 만료 시각이 아니다).
 * 컨테이너는 API 미연동 상태로 `expiresAt=null`을 전달해 배너를 항상 숨긴다 — BE가 참여자 자기
 * 조회 API를 제공하면 그 값을 연결한다. 컴포넌트 자체는 props만으로 완전히 동작한다.
 */
import { StyleSheet, Text, View } from 'react-native';

import { useTheme } from '../../theme/useTheme';

export interface GuestExpiryBannerProps {
  /** 본인 게스트 참여 만료 시각(ISO-8601). 게스트가 아니거나 정보가 없으면 null. */
  expiresAt: string | null;
}

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

/** `expiresAt`까지 남은 일수(올림). 이미 지났으면 0 이하를 반환한다. */
export function computeDaysRemaining(expiresAt: string, now: Date = new Date()): number {
  const diffMs = new Date(expiresAt).getTime() - now.getTime();
  return Math.ceil(diffMs / MILLISECONDS_PER_DAY);
}

export function GuestExpiryBanner({ expiresAt }: GuestExpiryBannerProps) {
  const { tokens } = useTheme();

  if (!expiresAt) {
    return null;
  }

  const daysRemaining = computeDaysRemaining(expiresAt);
  const label =
    daysRemaining > 0 ? `게스트 참여 만료: D-${daysRemaining}` : '게스트 참여 만료: D-day';

  return (
    <View
      style={[styles.container, { backgroundColor: tokens.surface }]}
      accessible
      accessibilityRole="alert"
      accessibilityLabel={label}
    >
      <Text style={[styles.message, { color: tokens.textSecondary }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  message: {
    fontSize: 13,
    fontWeight: '600',
  },
});
