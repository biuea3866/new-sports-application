/**
 * 상품 상세 화면
 */
import {
  View,
  Text,
  Image,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useProduct } from '../../../lib/useProduct';

export default function ProductDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data: product, isLoading, isError } = useProduct(id ?? '');

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="상품 상세 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError || product === undefined) {
    return (
      <View style={styles.centered} accessibilityLabel="상품 상세 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          상품 정보를 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      accessible={true}
      accessibilityLabel="상품 상세 화면"
    >
      {product.imageUrl.length > 0 && (
        <Image
          source={{ uri: product.imageUrl }}
          style={styles.image}
          accessibilityLabel={`${product.name} 이미지`}
          resizeMode="cover"
        />
      )}

      <View style={styles.content}>
        <Text style={styles.name} accessibilityRole="header">
          {product.name}
        </Text>

        <Text style={styles.price} accessibilityLabel={`가격 ${product.price}원`}>
          {Number(product.price).toLocaleString()}원
        </Text>

        <View style={styles.stockRow}>
          <Text style={styles.stockLabel}>재고</Text>
          <Text
            style={[
              styles.stockValue,
              product.stockQuantity === 0 && styles.stockOut,
            ]}
            accessibilityLabel={`재고 ${product.stockQuantity}개`}
          >
            {product.stockQuantity === 0 ? '품절' : `${product.stockQuantity}개`}
          </Text>
        </View>

        <Text style={styles.descriptionLabel} accessibilityRole="text">
          상품 설명
        </Text>
        <Text style={styles.description} accessibilityRole="text">
          {product.description}
        </Text>
      </View>
    </ScrollView>
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
    backgroundColor: '#fff',
  },
  image: {
    width: '100%',
    height: 280,
  },
  content: {
    padding: 20,
  },
  name: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  price: {
    fontSize: 20,
    fontWeight: '700',
    color: '#007AFF',
    marginBottom: 12,
  },
  stockRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  stockLabel: {
    fontSize: 14,
    color: '#3C3C43',
    marginRight: 8,
  },
  stockValue: {
    fontSize: 14,
    color: '#34C759',
    fontWeight: '600',
  },
  stockOut: {
    color: '#FF3B30',
  },
  descriptionLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#3C3C43',
    marginBottom: 8,
  },
  description: {
    fontSize: 15,
    color: '#1C1C1E',
    lineHeight: 22,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
});
