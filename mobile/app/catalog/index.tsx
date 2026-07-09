/**
 * CatalogScreen — 통합 상품 검색 화면 (`/catalog`, FE-09)
 *
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ①"·
 * "화면별 상태 표"·"컴포넌트 트리". 이미 머지된 조각(FE-04 useCatalogSearch, FE-06
 * CatalogItemCard, FE-08 CatalogSearchControls, FE-03 PartialFailureBanner, FE-12
 * catalog-navigation)을 배선하는 컨테이너 컴포넌트다.
 *
 * 4상태(loading/empty/error/success) + 부분 실패(failedDomains)를 처리한다.
 * 검색어·필터는 지역 상태(useState)만 다루고 전역 승격하지 않는다(no-global-by-default).
 * 데이터 가공(itemType 라벨 변환·라우트 매핑)은 컴포넌트 밖 유틸에 위임한다(no-logic-in-component).
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';

import type { CatalogItem, CatalogItemType, SellerType } from '../../api/catalog-types';
import { CatalogItemCard } from '../../components/catalog/CatalogItemCard';
import { CatalogSearchControls } from '../../components/catalog/CatalogSearchControls';
import { PartialFailureBanner } from '../../components/common/PartialFailureBanner';
import { EmptyState, ErrorView, LoadingView, ThemedText } from '../../components/ui';
import { CATALOG_ITEM_TYPE_LABEL } from '../../lib/catalog-format';
import { resolveCatalogRoute } from '../../lib/catalog-navigation';
import { useCatalogSearch } from '../../lib/useCatalogSearch';
import { useTheme } from '../../theme/useTheme';

const PAGE = 0;
const SIZE = 20;
const EMPTY_MESSAGE = '검색 결과가 없어요';
const EMPTY_DESCRIPTION = '다른 검색어나 필터를 시도해 보세요';
const ERROR_MESSAGE = '검색 결과를 불러오지 못했어요';

function handleItemPress(item: CatalogItem) {
  const route = resolveCatalogRoute(item.itemType, item.sourceId);
  if (route === null) {
    return;
  }
  router.push(route);
}

export default function CatalogScreen() {
  const { tokens } = useTheme();
  const [keyword, setKeyword] = useState('');
  const [itemType, setItemType] = useState<CatalogItemType | undefined>(undefined);
  const [sellerType, setSellerType] = useState<SellerType | undefined>(undefined);

  const { data, isLoading, isError, refetch } = useCatalogSearch({
    keyword: keyword.length > 0 ? keyword : undefined,
    itemType,
    sellerType,
    page: PAGE,
    size: SIZE,
  });

  const items = data?.items ?? [];
  const failedDomainLabels = (data?.failedDomains ?? []).map(
    (domain) => CATALOG_ITEM_TYPE_LABEL[domain]
  );

  return (
    <View
      testID="catalog-screen"
      style={[styles.container, { backgroundColor: tokens.background }]}
    >
      <View style={styles.header}>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로가기"
          style={styles.backButton}
        >
          <ThemedText variant="primary" style={styles.backIcon}>
            ←
          </ThemedText>
        </Pressable>
        <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
          통합 검색
        </ThemedText>
      </View>

      <CatalogSearchControls
        keyword={keyword}
        onKeywordChange={setKeyword}
        itemType={itemType}
        onItemTypeChange={setItemType}
        sellerType={sellerType}
        onSellerTypeChange={setSellerType}
      />

      <View style={styles.body}>
        {isLoading ? (
          <LoadingView variant="skeleton" />
        ) : isError ? (
          <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
        ) : items.length === 0 ? (
          <EmptyState message={EMPTY_MESSAGE} description={EMPTY_DESCRIPTION} />
        ) : (
          <FlatList
            data={items}
            keyExtractor={(item) => `${item.itemType}-${item.sourceId}`}
            ListHeaderComponent={
              failedDomainLabels.length > 0 ? (
                <PartialFailureBanner labels={failedDomainLabels} />
              ) : null
            }
            renderItem={({ item }) => (
              <CatalogItemCard item={item} onPress={() => handleItemPress(item)} />
            )}
            contentContainerStyle={styles.list}
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  backButton: {
    marginRight: 12,
    paddingVertical: 4,
  },
  backIcon: {
    fontSize: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
  },
  body: {
    flex: 1,
  },
  list: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 32,
  },
});
