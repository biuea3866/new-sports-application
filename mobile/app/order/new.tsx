/**
 * 주문 신청 화면 — 카트 확인 후 주문 생성
 */
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  Alert,
} from 'react-native';
import { router } from 'expo-router';
import { useMyCartQuery } from '../../lib/useCart';
import { useCreateGoodsOrderMutation } from '../../lib/useGoodsOrders';
import { ROUTES } from '../../lib/navigation';
import { useTheme } from '../../theme/useTheme';
import { createStyles } from '../../theme/createStyles';
import type { ThemeTokens } from '../../theme/tokens';

export default function OrderNewScreen() {
  const { data: cart, isLoading: isCartLoading, isError: isCartError } = useMyCartQuery();
  const createOrderMutation = useCreateGoodsOrderMutation();
  const { tokens } = useTheme();
  const styles = useStyles(tokens);

  function handleOrder() {
    if (cart === undefined) return;

    createOrderMutation.mutate(
      { cartId: cart.id },
      {
        onSuccess: () => {
          router.replace(ROUTES.order.list);
        },
        onError: () => {
          Alert.alert('주문 실패', '주문 처리 중 오류가 발생했습니다. 다시 시도해 주세요.');
        },
      }
    );
  }

  if (isCartLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="주문 정보 로딩 중">
        <ActivityIndicator size="large" color={tokens.accent} />
      </View>
    );
  }

  if (isCartError || cart === undefined) {
    return (
      <View style={styles.centered} accessibilityLabel="주문 정보 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          장바구니 정보를 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  if (cart.items.length === 0) {
    return (
      <View style={styles.centered} accessibilityLabel="빈 장바구니">
        <Text style={styles.emptyText} accessibilityRole="text">
          장바구니가 비어 있습니다.
        </Text>
        <TouchableOpacity
          style={styles.goCartButton}
          onPress={() => router.replace(ROUTES.cart)}
          accessibilityRole="button"
          accessibilityLabel="장바구니로 이동"
        >
          <Text style={styles.goCartButtonText}>장바구니로 이동</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const isSubmitting = createOrderMutation.isPending;

  return (
    <View style={styles.container} accessible={false}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.sectionTitle} accessibilityRole="header">
          주문 상품
        </Text>

        {cart.items.map((item) => (
          <View
            key={item.id}
            style={styles.itemRow}
            accessible={true}
            accessibilityLabel={`${item.productName} ${item.quantity}개 ${item.subtotal}원`}
          >
            <View style={styles.itemInfo}>
              <Text style={styles.itemName} numberOfLines={2}>
                {item.productName}
              </Text>
              <Text style={styles.itemMeta}>
                {Number(item.unitPrice).toLocaleString()}원 × {item.quantity}
              </Text>
            </View>
            <Text style={styles.itemSubtotal}>{Number(item.subtotal).toLocaleString()}원</Text>
          </View>
        ))}

        <View style={styles.divider} />

        <View style={styles.totalRow}>
          <Text style={styles.totalLabel}>결제 금액</Text>
          <Text style={styles.totalAmount} accessibilityLabel={`결제 금액 ${cart.totalAmount}원`}>
            {Number(cart.totalAmount).toLocaleString()}원
          </Text>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={[styles.orderButton, isSubmitting && styles.orderButtonDisabled]}
          onPress={handleOrder}
          disabled={isSubmitting}
          accessibilityRole="button"
          accessibilityLabel="주문 확인"
          accessibilityState={{ disabled: isSubmitting }}
        >
          {isSubmitting ? (
            <ActivityIndicator color={tokens.accentText} />
          ) : (
            <Text style={styles.orderButtonText}>
              {Number(cart.totalAmount).toLocaleString()}원 주문하기
            </Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  );
}

const useStyles = createStyles((theme: ThemeTokens) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: theme.background,
    },
    centered: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      gap: 16,
      backgroundColor: theme.background,
    },
    scrollContent: {
      padding: 16,
      paddingBottom: 8,
    },
    sectionTitle: {
      fontSize: 17,
      fontWeight: '700',
      color: theme.textPrimary,
      marginBottom: 14,
    },
    itemRow: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      paddingVertical: 10,
      gap: 12,
    },
    itemInfo: {
      flex: 1,
    },
    itemName: {
      fontSize: 14,
      color: theme.textPrimary,
      fontWeight: '500',
      marginBottom: 4,
    },
    itemMeta: {
      fontSize: 13,
      color: theme.textMuted,
    },
    itemSubtotal: {
      fontSize: 14,
      color: theme.textPrimary,
      fontWeight: '600',
    },
    divider: {
      height: 1,
      backgroundColor: theme.border,
      marginVertical: 14,
    },
    totalRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingBottom: 8,
    },
    totalLabel: {
      fontSize: 16,
      color: theme.textSecondary,
      fontWeight: '600',
    },
    totalAmount: {
      fontSize: 20,
      color: theme.accent,
      fontWeight: '700',
    },
    footer: {
      paddingHorizontal: 16,
      paddingVertical: 16,
      borderTopWidth: 1,
      borderTopColor: theme.border,
    },
    orderButton: {
      backgroundColor: theme.accent,
      borderRadius: 10,
      paddingVertical: 14,
      alignItems: 'center',
    },
    orderButtonDisabled: {
      backgroundColor: theme.disabled,
    },
    orderButtonText: {
      color: theme.accentText,
      fontSize: 16,
      fontWeight: '700',
    },
    errorText: {
      color: theme.danger,
      fontSize: 15,
    },
    emptyText: {
      color: theme.textMuted,
      fontSize: 15,
    },
    goCartButton: {
      paddingHorizontal: 20,
      paddingVertical: 10,
      borderRadius: 8,
      backgroundColor: theme.accent,
    },
    goCartButtonText: {
      color: theme.accentText,
      fontWeight: '600',
      fontSize: 14,
    },
  })
);
