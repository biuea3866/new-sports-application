/**
 * CatalogItemCard — 통합 검색 결과 한 항목(CatalogItem)을 렌더하는 프레젠테이션 카드.
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ①".
 *
 * itemType 라벨 배지(중립 tint) + 제목 + 가격(KRW, price=null이면 "가격 상세 확인") + 상대 시각.
 * PRODUCT 항목만 SellerTypeBadge를 노출한다(B2B 배지가 화면 유일 accent). 데이터 가공은
 * 컴포넌트 내부에 두지 않고 순수 유틸(catalog-format·post-format)에 위임한다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, StyleSheet } from 'react-native';
import type { CatalogItem } from '../../api/catalog-types';
import { Card, ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';
import { CATALOG_ITEM_TYPE_LABEL, formatCatalogPrice } from '../../lib/catalog-format';
import { formatRelativeTime } from '../../lib/post-format';
import { SellerTypeBadge } from './SellerTypeBadge';

export interface CatalogItemCardProps {
  item: CatalogItem;
  onPress: (detailPath: string) => void;
}

export function CatalogItemCard({ item, onPress }: CatalogItemCardProps) {
  const { tokens } = useTheme();
  const itemTypeLabel = CATALOG_ITEM_TYPE_LABEL[item.itemType];
  const priceText = formatCatalogPrice(item.price);
  const relativeTime = formatRelativeTime(item.createdAt);
  const showSellerTypeBadge = item.itemType === 'PRODUCT';

  return (
    <Card
      testID={`catalog-item-card-${item.itemType}-${item.sourceId}`}
      onPress={() => onPress(item.detailPath)}
      accessibilityLabel={`${item.title}, ${priceText}`}
      style={styles.card}
    >
      <View style={styles.badgeRow}>
        <View style={[styles.itemTypeBadge, { backgroundColor: tokens.surface }]}>
          <ThemedText variant="secondary" style={styles.itemTypeLabel}>
            {itemTypeLabel}
          </ThemedText>
        </View>
        {showSellerTypeBadge ? <SellerTypeBadge sellerType={item.sellerType} /> : null}
      </View>
      <ThemedText variant="primary" style={styles.title} numberOfLines={1}>
        {item.title}
      </ThemedText>
      <View style={styles.metaRow}>
        <ThemedText variant="secondary" style={styles.meta}>
          {priceText}
        </ThemedText>
        <ThemedText variant="secondary" style={styles.meta}>
          {relativeTime}
        </ThemedText>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 12,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  itemTypeBadge: {
    alignSelf: 'flex-start',
    paddingVertical: 3,
    paddingHorizontal: 8,
    borderRadius: 6,
  },
  itemTypeLabel: {
    fontSize: 11,
    fontWeight: '700',
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
    marginTop: 8,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 4,
  },
  meta: {
    fontSize: 13,
  },
});
