/**
 * ProgramCard — 시설 상세(A-F1) 시설상품(program) 목록 카드.
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-F1(가격 위계 최상단 · 정원 · 소요시간).
 * 카드 전체를 탭하면 슬롯 선택 화면(A-F2, `/booking/new?programId=`)으로 이동한다.
 * 색은 useTheme() 토큰만 경유한다.
 */
import { StyleSheet, View } from 'react-native';

import type { ProgramResponse } from '../../api/program';
import { Card, ThemedText } from '../ui';
import {
  formatProgramCapacity,
  formatProgramDuration,
  formatProgramPrice,
} from '../../lib/program-format';

export interface ProgramCardProps {
  program: ProgramResponse;
  onPress: () => void;
}

export function ProgramCard({ program, onPress }: ProgramCardProps) {
  const priceLabel = formatProgramPrice(program.price);
  const durationLabel = formatProgramDuration(program.durationMinutes);
  const capacityLabel = formatProgramCapacity(program.capacity);
  const accessibilityLabel = `${program.name}, ${priceLabel}, ${durationLabel}, ${capacityLabel}, 예약하기`;

  return (
    <Card
      testID={`program-card-${program.id}`}
      onPress={onPress}
      accessibilityLabel={accessibilityLabel}
      style={styles.card}
    >
      <ThemedText variant="primary" style={styles.name} numberOfLines={1}>
        {program.name}
      </ThemedText>
      <View style={styles.priceRow}>
        <ThemedText variant="primary" style={styles.price}>
          {priceLabel}
        </ThemedText>
        <ThemedText variant="secondary" style={styles.duration}>
          {`· ${durationLabel}`}
        </ThemedText>
      </View>
      <View style={styles.footerRow}>
        <ThemedText variant="secondary" style={styles.capacity}>
          {capacityLabel}
        </ThemedText>
        <ThemedText variant="accent" style={styles.cta}>
          예약
        </ThemedText>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 12,
  },
  name: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 4,
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  price: {
    fontSize: 18,
    fontWeight: '700',
  },
  duration: {
    fontSize: 13,
    fontWeight: '400',
    marginLeft: 6,
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
  },
  capacity: {
    fontSize: 13,
  },
  cta: {
    fontSize: 14,
    fontWeight: '700',
  },
});
