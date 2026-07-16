/**
 * OfflineBanner — 오프라인 상태일 때 화면 상단에 배너를 표시합니다.
 * isConnected가 false일 때만 노출됩니다.
 */
import { View, Text, StyleSheet } from 'react-native';
import { useNetInfo } from '../lib/netinfo';
import { useTheme } from '../theme/useTheme';
import { createStyles } from '../theme/createStyles';
import type { ThemeTokens } from '../theme/tokens';

export function OfflineBanner() {
  const netInfo = useNetInfo();
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  if (netInfo.isConnected !== false) {
    return null;
  }

  return (
    <View
      style={styles.container}
      accessibilityRole="alert"
      accessibilityLabel="오프라인 상태입니다"
    >
      <Text style={styles.text}>네트워크 연결이 없습니다</Text>
    </View>
  );
}

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      backgroundColor: theme.danger,
      paddingVertical: 8,
      paddingHorizontal: 16,
      alignItems: 'center',
    },
    text: {
      color: theme.accentText,
      fontSize: 14,
      fontWeight: '600',
    },
  })
);
