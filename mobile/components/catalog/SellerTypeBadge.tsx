/**
 * SellerTypeBadge — PRODUCT 항목의 판매자 유형 배지.
 * B2B(브랜드)는 화면 유일 accent 강조(설계 "화면당 accent 1곳" 원칙), B2C(중고)는 중립 톤.
 * sellerType이 null이면 아무것도 렌더하지 않는다(PRODUCT 외 항목은 호출부가 null을 전달).
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, StyleSheet } from 'react-native';
import type { SellerType } from '../../api/catalog-types';
import { ThemedText } from '../themed/ThemedText';
import { useTheme } from '../../theme/useTheme';

export interface SellerTypeBadgeProps {
  sellerType: SellerType | null;
}

const SELLER_TYPE_LABEL: Record<SellerType, string> = {
  B2B: '브랜드',
  B2C: '중고',
};

export function SellerTypeBadge({ sellerType }: SellerTypeBadgeProps) {
  const { tokens } = useTheme();

  if (sellerType === null) {
    return null;
  }

  const isBrand = sellerType === 'B2B';
  const backgroundColor = isBrand ? tokens.accent : tokens.surface;

  return (
    <View
      testID="seller-type-badge"
      style={[styles.badge, { backgroundColor }]}
      accessibilityRole="text"
    >
      <ThemedText variant={isBrand ? 'onAccent' : 'secondary'} style={styles.label}>
        {SELLER_TYPE_LABEL[sellerType]}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    paddingVertical: 3,
    paddingHorizontal: 8,
    borderRadius: 6,
  },
  label: {
    fontSize: 11,
    fontWeight: '700',
  },
});
