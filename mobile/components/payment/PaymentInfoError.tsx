/**
 * PaymentInfoError — 결제에 필요한 주문 정보(금액 등)가 없을 때 보여주는 오류 상태.
 *
 * 결제 금액을 모르는 채로 "0원"을 렌더하면 사용자가 실제 결제 금액으로 오인한다
 * (유즈케이스 캡쳐 19-결제-수단-선택). 금액이 확정되지 않은 진입은 결제 수단 선택 대신
 * 이 화면을 보여주고 되돌아가게 한다.
 *
 * 색은 항상 useTheme() 토큰을 경유한다(하드코딩 색 없음).
 */
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { createStyles } from '../../theme/createStyles';
import type { ThemeTokens } from '../../theme/tokens';
import { useTheme } from '../../theme/useTheme';

export const PAYMENT_INFO_ERROR_MESSAGE = '결제 정보를 불러올 수 없습니다.';

export interface PaymentInfoErrorProps {
  onGoBack: () => void;
}

export function PaymentInfoError({ onGoBack }: PaymentInfoErrorProps) {
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  return (
    <View style={styles.container} accessibilityLabel="결제 정보 오류">
      <Text style={styles.title} accessibilityRole="alert">
        {PAYMENT_INFO_ERROR_MESSAGE}
      </Text>
      <Text style={styles.description} accessibilityRole="text">
        주문 정보가 없어 결제를 진행할 수 없어요. 주문 내역에서 다시 시도해주세요.
      </Text>
      <TouchableOpacity
        style={styles.button}
        onPress={onGoBack}
        accessibilityRole="button"
        accessibilityLabel="돌아가기"
      >
        <Text style={styles.buttonLabel}>돌아가기</Text>
      </TouchableOpacity>
    </View>
  );
}

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      flex: 1,
      padding: 24,
      justifyContent: 'center',
      backgroundColor: theme.background,
    },
    title: {
      fontSize: 18,
      fontWeight: '700',
      color: theme.textPrimary,
      textAlign: 'center',
      marginBottom: 8,
    },
    description: {
      fontSize: 14,
      lineHeight: 22,
      color: theme.textMuted,
      textAlign: 'center',
      marginBottom: 32,
    },
    button: {
      backgroundColor: theme.accent,
      paddingVertical: 16,
      borderRadius: 8,
      alignItems: 'center',
    },
    buttonLabel: {
      color: theme.accentText,
      fontSize: 16,
      fontWeight: '700',
    },
  })
);
