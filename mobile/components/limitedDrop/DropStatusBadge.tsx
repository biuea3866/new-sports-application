/**
 * DropStatusBadge — 한정판 회차 status를 라벨·톤으로 표시하는 프레젠테이션 컴포넌트.
 * SCHEDULED=중립, OPEN=accent, SOLD_OUT=danger, CLOSED=muted.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { StyleSheet } from 'react-native';
import type { LimitedDropStatus } from '../../api/types';
import type { ThemedTextVariant } from '../themed/ThemedText';
import { ThemedText } from '../themed/ThemedText';
import { ThemedView } from '../themed/ThemedView';

export interface DropStatusBadgeProps {
  status: LimitedDropStatus;
}

interface StatusPresentation {
  label: string;
  variant: ThemedTextVariant;
}

const STATUS_PRESENTATION: Record<LimitedDropStatus, StatusPresentation> = {
  SCHEDULED: { label: '오픈예정', variant: 'secondary' },
  OPEN: { label: '판매중', variant: 'accent' },
  SOLD_OUT: { label: '재고소진', variant: 'danger' },
  CLOSED: { label: '판매종료', variant: 'muted' },
};

export function DropStatusBadge({ status }: DropStatusBadgeProps) {
  const { label, variant } = STATUS_PRESENTATION[status];

  return (
    <ThemedView background="surface" style={styles.badge}>
      <ThemedText variant={variant} style={styles.label}>
        {label}
      </ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 6,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
  },
});
