/**
 * 장바구니 화면
 */
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  Alert,
} from 'react-native';
import { router } from 'expo-router';
import {
  useMyCartQuery,
  useUpdateCartItemMutation,
  useRemoveCartItemMutation,
  useClearCartMutation,
} from '../../lib/useCart';
import { ROUTES } from '../../lib/navigation';
import type { CartItemResponse } from '../../api/types';

interface CartItemRowProps {
  item: CartItemResponse;
  onIncrease: () => void;
  onDecrease: () => void;
  onRemove: () => void;
  isUpdating: boolean;
}

function CartItemRow({
  item,
  onIncrease,
  onDecrease,
  onRemove,
  isUpdating,
}: CartItemRowProps) {
  return (
    <View style={styles.itemRow} accessible={true} accessibilityLabel={`${item.productName} 장바구니 항목`}>
      <View style={styles.itemInfo}>
        <Text style={styles.itemName} numberOfLines={2}>
          {item.productName}
        </Text>
        <Text style={styles.itemPrice} accessibilityLabel={`단가 ${item.unitPrice}원`}>
          {Number(item.unitPrice).toLocaleString()}원
        </Text>
        <Text style={styles.itemSubtotal} accessibilityLabel={`소계 ${item.subtotal}원`}>
          소계: {Number(item.subtotal).toLocaleString()}원
        </Text>
      </View>

      <View style={styles.itemControls}>
        <TouchableOpacity
          style={[styles.qtyButton, isUpdating && styles.qtyButtonDisabled]}
          onPress={onDecrease}
          disabled={isUpdating || item.quantity <= 1}
          accessibilityRole="button"
          accessibilityLabel="수량 감소"
          accessibilityState={{ disabled: isUpdating || item.quantity <= 1 }}
        >
          <Text style={styles.qtyButtonText}>-</Text>
        </TouchableOpacity>

        <Text style={styles.qty} accessibilityLabel={`수량 ${item.quantity}`}>
          {item.quantity}
        </Text>

        <TouchableOpacity
          style={[styles.qtyButton, isUpdating && styles.qtyButtonDisabled]}
          onPress={onIncrease}
          disabled={isUpdating}
          accessibilityRole="button"
          accessibilityLabel="수량 증가"
          accessibilityState={{ disabled: isUpdating }}
        >
          <Text style={styles.qtyButtonText}>+</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.removeButton}
          onPress={onRemove}
          disabled={isUpdating}
          accessibilityRole="button"
          accessibilityLabel={`${item.productName} 삭제`}
          accessibilityState={{ disabled: isUpdating }}
        >
          <Text style={styles.removeButtonText}>삭제</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

export default function CartScreen() {
  const { data: cart, isLoading, isError } = useMyCartQuery();
  const updateMutation = useUpdateCartItemMutation();
  const removeMutation = useRemoveCartItemMutation();
  const clearMutation = useClearCartMutation();

  function handleIncrease(itemId: number, currentQuantity: number) {
    updateMutation.mutate({ cartItemId: itemId, body: { quantity: currentQuantity + 1 } });
  }

  function handleDecrease(itemId: number, currentQuantity: number) {
    if (currentQuantity <= 1) return;
    updateMutation.mutate({ cartItemId: itemId, body: { quantity: currentQuantity - 1 } });
  }

  function handleRemove(itemId: number) {
    Alert.alert('항목 삭제', '장바구니에서 삭제하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => removeMutation.mutate(itemId),
      },
    ]);
  }

  function handleClear() {
    Alert.alert('장바구니 비우기', '장바구니를 모두 비우시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '비우기',
        style: 'destructive',
        onPress: () => clearMutation.mutate(),
      },
    ]);
  }

  function handleOrder() {
    router.push(ROUTES.order.new);
  }

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="장바구니 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError || cart === undefined) {
    return (
      <View style={styles.centered} accessibilityLabel="장바구니 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          장바구니를 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  const isMutating =
    updateMutation.isPending || removeMutation.isPending || clearMutation.isPending;

  return (
    <View style={styles.container} accessible={false}>
      <View style={styles.header}>
        <Text style={styles.title} accessibilityRole="header">
          장바구니
        </Text>
        {cart.items.length > 0 && (
          <TouchableOpacity
            onPress={handleClear}
            disabled={isMutating}
            accessibilityRole="button"
            accessibilityLabel="장바구니 비우기"
            accessibilityState={{ disabled: isMutating }}
          >
            <Text style={styles.clearButtonText}>비우기</Text>
          </TouchableOpacity>
        )}
      </View>

      {cart.items.length === 0 ? (
        <View style={styles.centered}>
          <Text style={styles.emptyText} accessibilityRole="text">
            장바구니가 비어 있습니다.
          </Text>
        </View>
      ) : (
        <>
          <FlatList
            data={cart.items}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => (
              <CartItemRow
                item={item}
                onIncrease={() => handleIncrease(item.id, item.quantity)}
                onDecrease={() => handleDecrease(item.id, item.quantity)}
                onRemove={() => handleRemove(item.id)}
                isUpdating={isMutating}
              />
            )}
            ItemSeparatorComponent={() => <View style={styles.separator} />}
            contentContainerStyle={styles.listContent}
          />

          <View style={styles.footer}>
            <View style={styles.totalRow}>
              <Text style={styles.totalLabel}>합계</Text>
              <Text
                style={styles.totalAmount}
                accessibilityLabel={`합계 ${cart.totalAmount}원`}
              >
                {Number(cart.totalAmount).toLocaleString()}원
              </Text>
            </View>

            <TouchableOpacity
              style={[styles.orderButton, isMutating && styles.orderButtonDisabled]}
              onPress={handleOrder}
              disabled={isMutating}
              accessibilityRole="button"
              accessibilityLabel="주문하기"
              accessibilityState={{ disabled: isMutating }}
            >
              <Text style={styles.orderButtonText}>주문하기</Text>
            </TouchableOpacity>
          </View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  title: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1C1C1E',
  },
  clearButtonText: {
    fontSize: 14,
    color: '#FF3B30',
    fontWeight: '600',
  },
  listContent: {
    paddingBottom: 8,
  },
  itemRow: {
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  itemInfo: {
    marginBottom: 10,
  },
  itemName: {
    fontSize: 15,
    color: '#1C1C1E',
    fontWeight: '500',
    marginBottom: 4,
  },
  itemPrice: {
    fontSize: 14,
    color: '#3C3C43',
    marginBottom: 2,
  },
  itemSubtotal: {
    fontSize: 13,
    color: '#8E8E93',
  },
  itemControls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  qtyButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#007AFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  qtyButtonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  qtyButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 20,
  },
  qty: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    minWidth: 24,
    textAlign: 'center',
  },
  removeButton: {
    marginLeft: 'auto',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#FF3B30',
  },
  removeButtonText: {
    fontSize: 13,
    color: '#FF3B30',
    fontWeight: '600',
  },
  separator: {
    height: 1,
    backgroundColor: '#E5E5EA',
    marginHorizontal: 16,
  },
  footer: {
    paddingHorizontal: 16,
    paddingVertical: 16,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
    gap: 14,
  },
  totalRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  totalLabel: {
    fontSize: 16,
    color: '#3C3C43',
    fontWeight: '600',
  },
  totalAmount: {
    fontSize: 20,
    color: '#007AFF',
    fontWeight: '700',
  },
  orderButton: {
    backgroundColor: '#007AFF',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
  },
  orderButtonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  orderButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
  emptyText: {
    color: '#8E8E93',
    fontSize: 15,
  },
});
