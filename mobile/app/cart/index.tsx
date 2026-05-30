/**
 * 장바구니 화면 — MO-06
 * - GET /cart/me: 장바구니 항목 목록 + 소계
 * - PATCH /cart/items/{itemId}: 수량 변경
 * - DELETE /cart/items/{itemId}: 항목 삭제
 * - POST /goods-orders: 주문 생성 → /payment 화면으로 이동
 */
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import {
  useCart,
  useUpdateCartItem,
  useRemoveCartItem,
  useCreateGoodsOrder,
  useCurrentUserId,
  CartItemDto,
} from '../../api/goods';

/** RFC 4122 v4 UUID 생성 (crypto 미설치 환경 대응) */
function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

interface CartItemRowProps {
  item: CartItemDto;
  onIncrease: () => void;
  onDecrease: () => void;
  onRemove: () => void;
  isPending: boolean;
}

function CartItemRow({ item, onIncrease, onDecrease, onRemove, isPending }: CartItemRowProps) {
  return (
    <View style={styles.itemRow} accessible accessibilityLabel={`상품 ID ${item.productId}, 수량 ${item.quantity}`}>
      <View style={styles.itemInfo}>
        <Text style={styles.itemProductId} accessibilityRole="text">
          상품 #{item.productId}
        </Text>
        <Text style={styles.itemQuantityLabel} accessibilityRole="text">
          수량: {item.quantity}
        </Text>
      </View>
      <View style={styles.itemActions}>
        <TouchableOpacity
          style={styles.quantityButton}
          onPress={onDecrease}
          disabled={isPending || item.quantity <= 1}
          accessibilityRole="button"
          accessibilityLabel={`상품 ${item.productId} 수량 감소`}
          accessibilityState={{ disabled: isPending || item.quantity <= 1 }}
        >
          <Text style={styles.quantityButtonText}>-</Text>
        </TouchableOpacity>
        <Text style={styles.quantityValue} accessibilityRole="text">
          {item.quantity}
        </Text>
        <TouchableOpacity
          style={styles.quantityButton}
          onPress={onIncrease}
          disabled={isPending}
          accessibilityRole="button"
          accessibilityLabel={`상품 ${item.productId} 수량 증가`}
          accessibilityState={{ disabled: isPending }}
        >
          <Text style={styles.quantityButtonText}>+</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.removeButton}
          onPress={onRemove}
          disabled={isPending}
          accessibilityRole="button"
          accessibilityLabel={`상품 ${item.productId} 삭제`}
          accessibilityState={{ disabled: isPending }}
        >
          <Text style={styles.removeButtonText}>삭제</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

export default function CartScreen() {
  const router = useRouter();
  const userId = useCurrentUserId();

  const { data: cart, isLoading, isError, refetch } = useCart(userId);
  const updateCartItem = useUpdateCartItem(userId);
  const removeCartItem = useRemoveCartItem(userId);
  const createGoodsOrder = useCreateGoodsOrder(userId);

  const isMutating =
    updateCartItem.isPending || removeCartItem.isPending || createGoodsOrder.isPending;

  // 합계 계산 (상품 가격 정보가 CartItemDto에 없으므로 수량 합계만 표시)
  const totalQuantity = useMemo(
    () => cart?.items.reduce((acc, item) => acc + item.quantity, 0) ?? 0,
    [cart]
  );

  const handleOrder = () => {
    if (!cart || cart.items.length === 0) {
      Alert.alert('장바구니가 비어 있습니다.', '상품을 추가한 후 주문하세요.');
      return;
    }

    const idempotencyKey = generateUUID();

    createGoodsOrder.mutate(
      {
        body: {
          method: 'CREDIT_CARD',
          fromCart: true,
          items: cart.items.map((item) => ({
            productId: item.productId,
            quantity: item.quantity,
          })),
        },
        idempotencyKey,
      },
      {
        onSuccess: (order) => {
          router.push(
            `/payment?orderType=GOODS&orderId=${order.id}&amount=${order.totalAmount}&method=CREDIT_CARD`
          );
        },
        onError: () => {
          Alert.alert('주문 실패', '주문 생성에 실패했습니다. 다시 시도해 주세요.');
        },
      }
    );
  };

  if (isLoading) {
    return (
      <View style={styles.center} accessible accessibilityLabel="장바구니 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.center} accessible accessibilityLabel="장바구니 오류">
        <Text style={styles.errorText}>장바구니를 불러오지 못했습니다.</Text>
        <TouchableOpacity
          style={styles.retryButton}
          onPress={() => void refetch()}
          accessibilityRole="button"
          accessibilityLabel="다시 시도"
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const items = cart?.items ?? [];

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <TouchableOpacity
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
        >
          <Text style={styles.backText}>{'< 뒤로'}</Text>
        </TouchableOpacity>
        <Text style={styles.header} accessibilityRole="header">
          장바구니
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      <FlatList
        data={items}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <CartItemRow
            item={item}
            isPending={isMutating}
            onIncrease={() =>
              updateCartItem.mutate({ itemId: item.id, quantity: item.quantity + 1 })
            }
            onDecrease={() =>
              updateCartItem.mutate({ itemId: item.id, quantity: item.quantity - 1 })
            }
            onRemove={() =>
              Alert.alert('항목 삭제', '이 상품을 장바구니에서 삭제하시겠습니까?', [
                { text: '취소', style: 'cancel' },
                { text: '삭제', style: 'destructive', onPress: () => removeCartItem.mutate(item.id) },
              ])
            }
          />
        )}
        ListEmptyComponent={
          <View style={styles.emptyContainer} accessible accessibilityLabel="장바구니가 비어 있습니다">
            <Text style={styles.emptyText}>장바구니가 비어 있습니다.</Text>
            <TouchableOpacity
              style={styles.shopButton}
              onPress={() => router.push('/(tabs)/store')}
              accessibilityRole="button"
              accessibilityLabel="쇼핑하러 가기"
            >
              <Text style={styles.shopButtonText}>쇼핑하러 가기</Text>
            </TouchableOpacity>
          </View>
        }
        contentContainerStyle={styles.listContent}
      />

      {items.length > 0 && (
        <View style={styles.footer}>
          <Text style={styles.totalText} accessibilityRole="text">
            총 {totalQuantity}개 상품
          </Text>
          <TouchableOpacity
            style={[styles.orderButton, (isMutating || createGoodsOrder.isPending) && styles.buttonDisabled]}
            onPress={handleOrder}
            disabled={isMutating || createGoodsOrder.isPending}
            accessibilityRole="button"
            accessibilityLabel="주문하기"
            accessibilityState={{ disabled: isMutating || createGoodsOrder.isPending }}
          >
            <Text style={styles.orderButtonText}>
              {createGoodsOrder.isPending ? '주문 중...' : '주문하기'}
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  backText: {
    color: '#007AFF',
    fontSize: 16,
    width: 60,
  },
  header: {
    flex: 1,
    textAlign: 'center',
    fontSize: 18,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  headerSpacer: {
    width: 60,
  },
  listContent: {
    padding: 16,
    flexGrow: 1,
  },
  itemRow: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 16,
    marginBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  itemInfo: {
    flex: 1,
  },
  itemProductId: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  itemQuantityLabel: {
    fontSize: 13,
    color: '#8E8E93',
  },
  itemActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  quantityButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#F2F2F7',
    alignItems: 'center',
    justifyContent: 'center',
  },
  quantityButtonText: {
    fontSize: 18,
    color: '#1C1C1E',
    fontWeight: '600',
  },
  quantityValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    minWidth: 24,
    textAlign: 'center',
  },
  removeButton: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: '#FFE5E5',
    borderRadius: 6,
  },
  removeButtonText: {
    color: '#FF3B30',
    fontSize: 13,
    fontWeight: '600',
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 80,
  },
  emptyText: {
    fontSize: 16,
    color: '#8E8E93',
    marginBottom: 20,
  },
  shopButton: {
    backgroundColor: '#007AFF',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
  },
  shopButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
  },
  footer: {
    backgroundColor: '#fff',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
  },
  totalText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 12,
    textAlign: 'right',
  },
  orderButton: {
    backgroundColor: '#007AFF',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  orderButtonText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '700',
  },
  errorText: {
    fontSize: 16,
    color: '#8E8E93',
    marginBottom: 16,
  },
  retryButton: {
    backgroundColor: '#007AFF',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 8,
  },
  retryButtonText: {
    color: '#fff',
    fontWeight: '600',
  },
});
