/**
 * RecruitmentCard — 모집 목록(A-R1) 카드. 제목·정원·참가비·마감 D-day·상태 배지를 표시한다.
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-R1. 색은 useTheme() 토큰만 경유한다.
 */
import { StyleSheet, View } from 'react-native';

import type { RecruitmentResponse } from '../../api/recruitment';
import { Card, ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';
import {
  RECRUITMENT_STATUS_LABEL,
  formatDeadlineDday,
  formatFeeAmount,
} from '../../lib/recruitment-format';

export interface RecruitmentCardProps {
  recruitment: RecruitmentResponse;
  onPress: () => void;
}

export function RecruitmentCard({ recruitment, onPress }: RecruitmentCardProps) {
  const { tokens } = useTheme();
  const statusLabel = RECRUITMENT_STATUS_LABEL[recruitment.status];
  const badgeColor = recruitment.status === 'OPEN' ? tokens.success : tokens.disabled;
  const metaLine = `정원 ${recruitment.capacity} · ${formatFeeAmount(
    recruitment.feeAmount
  )} · ${formatDeadlineDday(recruitment.applicationDeadline)} 마감`;

  return (
    <Card
      testID={`recruitment-card-${recruitment.id}`}
      onPress={onPress}
      accessibilityLabel={`${recruitment.title}, ${metaLine}, ${statusLabel}`}
      style={styles.card}
    >
      <ThemedText variant="primary" style={styles.title} numberOfLines={1}>
        {recruitment.title}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.meta}>
        {metaLine}
      </ThemedText>
      <View style={[styles.badge, { backgroundColor: badgeColor }]}>
        <ThemedText variant="onAccent" style={styles.badgeLabel}>
          {statusLabel}
        </ThemedText>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 12,
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
  },
  meta: {
    fontSize: 13,
    marginTop: 4,
  },
  badge: {
    alignSelf: 'flex-start',
    marginTop: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  badgeLabel: {
    fontSize: 11,
    fontWeight: '700',
  },
});
