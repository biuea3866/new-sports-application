/**
 * ListItem — surface 배경의 리스트 아이템. title/subtitle과 leading/trailing 슬롯을 렌더합니다.
 * onPress 지정 시 탭 가능한 행이 됩니다. 색은 항상 useTheme() 토큰을 경유합니다.
 */
import type { ReactNode } from 'react';
import { Pressable, View, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';

export interface ListItemProps {
  title: string;
  subtitle?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
  onPress?: () => void;
}

export function ListItem({ title, subtitle, leading, trailing, onPress }: ListItemProps) {
  const { tokens } = useTheme();
  const accessibilityLabel = subtitle ? `${title}, ${subtitle}` : title;

  const content = (
    <>
      {leading ? <View style={styles.leading}>{leading}</View> : null}
      <View style={styles.body}>
        <ThemedText variant="primary" style={styles.title} numberOfLines={1}>
          {title}
        </ThemedText>
        {subtitle ? (
          <ThemedText variant="secondary" style={styles.subtitle} numberOfLines={1}>
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
      {trailing ? <View style={styles.trailing}>{trailing}</View> : null}
    </>
  );

  if (onPress) {
    return (
      <Pressable
        style={[styles.row, { backgroundColor: tokens.surface }]}
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
      >
        {content}
      </Pressable>
    );
  }

  return <View style={[styles.row, { backgroundColor: tokens.surface }]}>{content}</View>;
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 12,
  },
  leading: {
    marginRight: 12,
  },
  body: {
    flex: 1,
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
  },
  subtitle: {
    fontSize: 13,
    marginTop: 2,
  },
  trailing: {
    marginLeft: 12,
    alignItems: 'flex-end',
  },
});
