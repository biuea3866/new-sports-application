/**
 * 스토어 탭 — 상품 목록 + 카테고리 필터 + 페이지 이동
 */
import {
  View,
  Text,
  FlatList,
  Image,
  TouchableOpacity,
  ActivityIndicator,
  ScrollView,
  StyleSheet,
} from 'react-native';
import { router } from 'expo-router';
import { useState } from 'react';
import { useProducts } from '../../lib/useProducts';
import { ROUTES } from '../../lib/navigation';
import type { ProductCategory, ProductSummary } from '../../api/types';

type FilterCategory = ProductCategory | 'ALL';

interface CategoryChipProps {
  label: string;
  selected: boolean;
  onPress: () => void;
}

function CategoryChip({ label, selected, onPress }: CategoryChipProps) {
  return (
    <TouchableOpacity
      style={[styles.chip, selected && styles.chipSelected]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${label} 카테고리 필터`}
      accessibilityState={{ selected }}
    >
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{label}</Text>
    </TouchableOpacity>
  );
}

interface ProductCardProps {
  product: ProductSummary;
  onPress: () => void;
}

function ProductCard({ product, onPress }: ProductCardProps) {
  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${product.name}, ${Number(product.price).toLocaleString()}원`}
    >
      {product.imageUrl.length > 0 && (
        <Image
          source={{ uri: product.imageUrl }}
          style={styles.cardImage}
          accessibilityLabel={`${product.name} 이미지`}
          resizeMode="cover"
        />
      )}
      <View style={styles.cardContent}>
        <Text style={styles.cardName} numberOfLines={2}>
          {product.name}
        </Text>
        <Text style={styles.cardPrice} accessibilityLabel={`가격 ${product.price}원`}>
          {Number(product.price).toLocaleString()}원
        </Text>
        {product.stockQuantity === 0 && (
          <Text style={styles.soldOut} accessibilityRole="text">
            품절
          </Text>
        )}
      </View>
    </TouchableOpacity>
  );
}

const PAGE_SIZE = 20;

const CATEGORY_OPTIONS: { label: string; value: FilterCategory }[] = [
  { label: '전체', value: 'ALL' },
  { label: '장비', value: 'EQUIPMENT' },
  { label: '의류', value: 'APPAREL' },
  { label: '신발', value: 'FOOTWEAR' },
  { label: '액세서리', value: 'ACCESSORY' },
];

export default function StoreScreen() {
  const [selectedCategory, setSelectedCategory] = useState<FilterCategory>('ALL');
  const [page, setPage] = useState(0);

  const category = selectedCategory === 'ALL' ? undefined : selectedCategory;
  const { data, isLoading, isError } = useProducts(page, PAGE_SIZE, category);

  function handleCategoryPress(value: FilterCategory) {
    setSelectedCategory(value);
    setPage(0);
  }

  function handleProductPress(id: number) {
    router.push(ROUTES.product.detail(String(id)));
  }

  return (
    <View style={styles.container} accessible={false}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.chipRow}
        contentContainerStyle={styles.chipRowContent}
        accessibilityRole="toolbar"
        accessibilityLabel="카테고리 필터"
      >
        {CATEGORY_OPTIONS.map((option) => (
          <CategoryChip
            key={option.value}
            label={option.label}
            selected={selectedCategory === option.value}
            onPress={() => handleCategoryPress(option.value)}
          />
        ))}
      </ScrollView>

      {isLoading && (
        <View style={styles.centered} accessibilityLabel="상품 목록 로딩 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && (
        <View style={styles.centered}>
          <Text style={styles.errorText} accessibilityRole="alert">
            상품 목록을 불러오지 못했습니다.
          </Text>
        </View>
      )}

      {!isLoading && !isError && data !== undefined && (
        <>
          <FlatList
            data={data.content}
            keyExtractor={(item) => String(item.id)}
            numColumns={2}
            columnWrapperStyle={styles.columnWrapper}
            contentContainerStyle={styles.listContent}
            renderItem={({ item }) => (
              <ProductCard product={item} onPress={() => handleProductPress(item.id)} />
            )}
            ListEmptyComponent={
              <View style={styles.centered}>
                <Text style={styles.emptyText} accessibilityRole="text">
                  상품이 없습니다.
                </Text>
              </View>
            }
          />

          <View style={styles.pagination} accessibilityRole="toolbar" accessibilityLabel="페이지 이동">
            <TouchableOpacity
              style={[styles.pageButton, page === 0 && styles.pageButtonDisabled]}
              onPress={() => setPage((prev) => prev - 1)}
              disabled={page === 0}
              accessibilityRole="button"
              accessibilityLabel="이전 페이지"
              accessibilityState={{ disabled: page === 0 }}
            >
              <Text style={[styles.pageButtonText, page === 0 && styles.pageButtonTextDisabled]}>
                이전
              </Text>
            </TouchableOpacity>

            <Text style={styles.pageInfo} accessibilityLabel={`${page + 1} / ${data.totalPages} 페이지`}>
              {page + 1} / {data.totalPages}
            </Text>

            <TouchableOpacity
              style={[
                styles.pageButton,
                page >= data.totalPages - 1 && styles.pageButtonDisabled,
              ]}
              onPress={() => setPage((prev) => prev + 1)}
              disabled={page >= data.totalPages - 1}
              accessibilityRole="button"
              accessibilityLabel="다음 페이지"
              accessibilityState={{ disabled: page >= data.totalPages - 1 }}
            >
              <Text
                style={[
                  styles.pageButtonText,
                  page >= data.totalPages - 1 && styles.pageButtonTextDisabled,
                ]}
              >
                다음
              </Text>
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
  chipRow: {
    flexGrow: 0,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  chipRowContent: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 8,
    flexDirection: 'row',
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#C7C7CC',
    backgroundColor: '#fff',
  },
  chipSelected: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  chipText: {
    fontSize: 13,
    color: '#3C3C43',
  },
  chipTextSelected: {
    color: '#fff',
    fontWeight: '600',
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
  emptyText: {
    color: '#8E8E93',
    fontSize: 15,
  },
  listContent: {
    padding: 12,
  },
  columnWrapper: {
    gap: 12,
    marginBottom: 12,
  },
  card: {
    flex: 1,
    borderRadius: 10,
    backgroundColor: '#F2F2F7',
    overflow: 'hidden',
  },
  cardImage: {
    width: '100%',
    height: 130,
  },
  cardContent: {
    padding: 10,
  },
  cardName: {
    fontSize: 13,
    color: '#1C1C1E',
    marginBottom: 4,
    fontWeight: '500',
  },
  cardPrice: {
    fontSize: 14,
    color: '#007AFF',
    fontWeight: '700',
  },
  soldOut: {
    fontSize: 12,
    color: '#FF3B30',
    marginTop: 2,
    fontWeight: '600',
  },
  pagination: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
    gap: 20,
  },
  pageButton: {
    paddingHorizontal: 20,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: '#007AFF',
  },
  pageButtonDisabled: {
    backgroundColor: '#E5E5EA',
  },
  pageButtonText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 14,
  },
  pageButtonTextDisabled: {
    color: '#8E8E93',
  },
  pageInfo: {
    fontSize: 14,
    color: '#3C3C43',
    minWidth: 60,
    textAlign: 'center',
  },
});
