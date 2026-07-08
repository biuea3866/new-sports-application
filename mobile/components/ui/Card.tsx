/**
 * Card — surface 배경의 카드 컨테이너. onPress 지정 시 탭 가능한 카드가 됩니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import type { ReactNode } from 'react';
import { Pressable, View, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export interface CardProps {
  children: ReactNode;
  onPress?: () => void;
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

export function Card({ children, onPress, accessibilityLabel, style, testID }: CardProps) {
  const { tokens } = useTheme();
  const cardStyle = [styles.card, { backgroundColor: tokens.surface }, style];

  if (onPress) {
    return (
      <Pressable
        testID={testID}
        style={cardStyle}
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
      >
        {children}
      </Pressable>
    );
  }

  return (
    <View testID={testID} style={cardStyle}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 12,
    padding: 16,
  },
});
