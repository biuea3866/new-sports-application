/**
 * 스토어 탭 — 상품 목록
 * MO-05 구현체
 */
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useProducts, ProductWithStock } from '../../api/goods';

interface ProductCardProps {
  product: ProductWithStock;
  onPress: () => void;
}

function ProductCard({ product, onPress }: ProductCardProps) {
  const isOutOfStock = product.stockQuantity === 0;

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${product.name}, ${product.price.toLocaleString()}원${isOutOfStock ? ', 품절' : ''}`}
    >
      <View style={styles.cardImagePlaceholder} accessibilityElementsHidden>
        <Text style={styles.cardImageText}>IMG</Text>
      </View>
      <View style={styles.cardInfo}>
        <Text style={styles.cardName} numberOfLines={2}>
          {product.name}
        </Text>
        <Text style={styles.cardPrice}>{product.price.toLocaleString()}원</Text>
        {isOutOfStock && (
          <Text style={styles.outOfStock} accessibilityRole="text">
            품절
          </Text>
        )}
      </View>
    </TouchableOpacity>
  );
}

export default function StoreScreen() {
  const router = useRouter();
  const { data: products, isLoading, isError, refetch } = useProducts();

  if (isLoading) {
    return (
      <View style={styles.center} accessible accessibilityLabel="상품 목록 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.center} accessible accessibilityLabel="상품 목록 오류">
        <Text style={styles.errorText}>상품 목록을 불러오지 못했습니다.</Text>
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

  return (
    <View style={styles.container} accessible={false}>
      <Text style={styles.header} accessibilityRole="header">
        스토어
      </Text>
      <FlatList
        data={products ?? []}
        keyExtractor={(item) => String(item.id)}
        numColumns={2}
        columnWrapperStyle={styles.row}
        renderItem={({ item }) => (
          <ProductCard
            product={item}
            onPress={() => router.push(`/product/${item.id}`)}
          />
        )}
        ListEmptyComponent={
          <Text style={styles.emptyText} accessibilityRole="text">
            등록된 상품이 없습니다.
          </Text>
        }
        contentContainerStyle={styles.listContent}
      />
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
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
    backgroundColor: '#fff',
  },
  listContent: {
    padding: 8,
  },
  row: {
    justifyContent: 'space-between',
  },
  card: {
    flex: 1,
    margin: 4,
    backgroundColor: '#fff',
    borderRadius: 10,
    overflow: 'hidden',
  },
  cardImagePlaceholder: {
    height: 140,
    backgroundColor: '#E5E5EA',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardImageText: {
    color: '#C7C7CC',
    fontSize: 12,
  },
  cardInfo: {
    padding: 10,
  },
  cardName: {
    fontSize: 14,
    fontWeight: '500',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  cardPrice: {
    fontSize: 14,
    fontWeight: '700',
    color: '#007AFF',
  },
  outOfStock: {
    fontSize: 12,
    color: '#FF3B30',
    marginTop: 2,
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
  emptyText: {
    textAlign: 'center',
    color: '#8E8E93',
    marginTop: 40,
    fontSize: 15,
  },
});
