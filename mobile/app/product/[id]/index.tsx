/**
 * 상품 상세 화면 — MO-05/MO-06
 * - 상품 정보 표시 (read-only)
 * - "장바구니 담기" 버튼 → POST /cart/items
 * - "장바구니 보기" → /cart
 */
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { useProducts, useAddCartItem, useCurrentUserId } from '../../../api/goods';

export default function ProductDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const userId = useCurrentUserId();
  const [quantity, setQuantity] = useState(1);

  const { data: products, isLoading, isError } = useProducts();
  const addCartItem = useAddCartItem(userId);

  const productId = Number(id);
  const product = products?.find((p) => p.id === productId);

  if (isLoading) {
    return (
      <View style={styles.center} accessible accessibilityLabel="상품 정보 로딩 중">
        <Text style={styles.loadingText}>로딩 중...</Text>
      </View>
    );
  }

  if (isError || !product) {
    return (
      <View style={styles.center} accessible accessibilityLabel="상품 정보를 불러오지 못했습니다">
        <Text style={styles.errorText}>상품 정보를 불러오지 못했습니다.</Text>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
        >
          <Text style={styles.backButtonText}>뒤로 가기</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const isOutOfStock = product.stockQuantity === 0;

  const handleAddToCart = () => {
    if (userId <= 0) {
      Alert.alert('로그인 필요', '장바구니를 이용하려면 로그인이 필요합니다.');
      return;
    }
    if (isOutOfStock) {
      Alert.alert('품절', '현재 재고가 없습니다.');
      return;
    }
    addCartItem.mutate(
      { productId: product.id, quantity },
      {
        onSuccess: () => {
          Alert.alert('장바구니 담기', `${product.name}이(가) 장바구니에 담겼습니다.`, [
            { text: '계속 쇼핑', style: 'cancel' },
            { text: '장바구니 보기', onPress: () => router.push('/cart') },
          ]);
        },
        onError: () => {
          Alert.alert('오류', '장바구니 담기에 실패했습니다. 다시 시도해 주세요.');
        },
      }
    );
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      accessible={false}
    >
      <TouchableOpacity
        style={styles.backButton}
        onPress={() => router.back()}
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
      >
        <Text style={styles.backButtonText}>{'< 뒤로'}</Text>
      </TouchableOpacity>

      {/* 상품 이미지 플레이스홀더 */}
      <View style={styles.imagePlaceholder} accessibilityElementsHidden>
        <Text style={styles.imagePlaceholderText}>상품 이미지</Text>
      </View>

      <View style={styles.infoSection}>
        <Text style={styles.productName} accessibilityRole="header">
          {product.name}
        </Text>
        <Text style={styles.productPrice} accessibilityRole="text">
          {product.price.toLocaleString()}원
        </Text>
        {isOutOfStock && (
          <Text style={styles.outOfStock} accessibilityRole="text">
            품절
          </Text>
        )}
        <Text style={styles.productDescription} accessibilityRole="text">
          {product.description}
        </Text>
      </View>

      {/* 수량 선택 */}
      <View style={styles.quantitySection}>
        <Text style={styles.quantityLabel} accessibilityRole="text">
          수량
        </Text>
        <View style={styles.quantityControl}>
          <TouchableOpacity
            style={styles.quantityButton}
            onPress={() => setQuantity((q) => Math.max(1, q - 1))}
            disabled={quantity <= 1}
            accessibilityRole="button"
            accessibilityLabel="수량 감소"
            accessibilityState={{ disabled: quantity <= 1 }}
          >
            <Text style={styles.quantityButtonText}>-</Text>
          </TouchableOpacity>
          <Text style={styles.quantityValue} accessibilityRole="text" accessibilityLabel={`수량 ${quantity}`}>
            {quantity}
          </Text>
          <TouchableOpacity
            style={styles.quantityButton}
            onPress={() => setQuantity((q) => Math.min(product.stockQuantity, q + 1))}
            disabled={quantity >= product.stockQuantity}
            accessibilityRole="button"
            accessibilityLabel="수량 증가"
            accessibilityState={{ disabled: quantity >= product.stockQuantity }}
          >
            <Text style={styles.quantityButtonText}>+</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* 장바구니 담기 버튼 */}
      <TouchableOpacity
        style={[styles.addToCartButton, (isOutOfStock || addCartItem.isPending) && styles.buttonDisabled]}
        onPress={handleAddToCart}
        disabled={isOutOfStock || addCartItem.isPending}
        accessibilityRole="button"
        accessibilityLabel={isOutOfStock ? '품절로 장바구니 담기 불가' : '장바구니 담기'}
        accessibilityState={{ disabled: isOutOfStock || addCartItem.isPending }}
      >
        <Text style={styles.addToCartButtonText}>
          {addCartItem.isPending ? '담는 중...' : '장바구니 담기'}
        </Text>
      </TouchableOpacity>

      {/* 장바구니 바로가기 */}
      <TouchableOpacity
        style={styles.viewCartButton}
        onPress={() => router.push('/cart')}
        accessibilityRole="button"
        accessibilityLabel="장바구니 보기"
      >
        <Text style={styles.viewCartButtonText}>장바구니 보기</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    paddingBottom: 40,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  imagePlaceholder: {
    height: 280,
    backgroundColor: '#E5E5EA',
    alignItems: 'center',
    justifyContent: 'center',
  },
  imagePlaceholderText: {
    color: '#C7C7CC',
    fontSize: 14,
  },
  infoSection: {
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  productName: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  productPrice: {
    fontSize: 20,
    fontWeight: '700',
    color: '#007AFF',
    marginBottom: 6,
  },
  outOfStock: {
    fontSize: 14,
    color: '#FF3B30',
    fontWeight: '600',
    marginBottom: 8,
  },
  productDescription: {
    fontSize: 15,
    color: '#3A3A3C',
    lineHeight: 22,
    marginTop: 12,
  },
  quantitySection: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  quantityLabel: {
    fontSize: 16,
    color: '#1C1C1E',
  },
  quantityControl: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  quantityButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#F2F2F7',
    alignItems: 'center',
    justifyContent: 'center',
  },
  quantityButtonText: {
    fontSize: 20,
    color: '#1C1C1E',
    fontWeight: '600',
  },
  quantityValue: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1C1C1E',
    minWidth: 28,
    textAlign: 'center',
  },
  addToCartButton: {
    marginHorizontal: 20,
    marginTop: 24,
    backgroundColor: '#007AFF',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  addToCartButtonText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '700',
  },
  viewCartButton: {
    marginHorizontal: 20,
    marginTop: 12,
    backgroundColor: '#F2F2F7',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  viewCartButtonText: {
    color: '#007AFF',
    fontSize: 17,
    fontWeight: '600',
  },
  backButton: {
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  backButtonText: {
    color: '#007AFF',
    fontSize: 16,
  },
  loadingText: {
    fontSize: 16,
    color: '#8E8E93',
  },
  errorText: {
    fontSize: 16,
    color: '#8E8E93',
    marginBottom: 16,
  },
});
