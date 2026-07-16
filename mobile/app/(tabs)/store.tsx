/**
 * 스토어 탭 — 굿즈|티켓 세그먼트 통합 화면.
 *
 * 근거: 사용자 피드백 "스토어 = 기존 스토어(굿즈) + 티켓을 세그먼트 컨트롤로 통합
 * (한 화면에서 굿즈 | 티켓 전환)". 홈의 "다가오는 경기" 카드는 이 탭을
 * `?segment=tickets` 쿼리로 열어 티켓 세그먼트를 바로 보여준다.
 *
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { useState } from 'react';
import { FlatList, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Image } from 'expo-image';

import { useProducts, type ProductWithStock } from '../../api/goods';
import { useEvents } from '../../lib/useEvents';
import { ROUTES } from '../../lib/navigation';
import type { EventResponse, EventStatus } from '../../api/types';
import {
  EmptyState,
  ErrorView,
  LoadingView,
  SegmentedControl,
  ThemedText,
  ThemedView,
} from '../../components/ui';
import { useTheme } from '../../theme/useTheme';

type StoreSegment = 'goods' | 'tickets';

const SEGMENT_OPTIONS = [
  { label: '굿즈', value: 'goods' },
  { label: '티켓', value: 'tickets' },
];

function isStoreSegment(value: unknown): value is StoreSegment {
  return value === 'goods' || value === 'tickets';
}

interface ProductCardProps {
  product: ProductWithStock;
  onPress: () => void;
}

function ProductCard({ product, onPress }: ProductCardProps) {
  const { tokens } = useTheme();
  const isOutOfStock = product.stockQuantity === 0;
  const [imageLoadFailed, setImageLoadFailed] = useState(false);
  const hasImage = product.imageUrl.length > 0 && !imageLoadFailed;

  return (
    <Pressable
      style={[styles.productCard, { backgroundColor: tokens.surface }]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${product.name}, ${product.price.toLocaleString()}원${isOutOfStock ? ', 품절' : ''}`}
    >
      {hasImage ? (
        <Image
          testID={`product-image-${product.id}`}
          source={{ uri: product.imageUrl }}
          style={styles.productImage}
          contentFit="cover"
          accessibilityElementsHidden
          onError={() => setImageLoadFailed(true)}
        />
      ) : (
        <View
          style={[styles.productImagePlaceholder, { backgroundColor: tokens.border }]}
          accessibilityElementsHidden
        >
          <ThemedText variant="muted" style={styles.productImageText}>
            IMG
          </ThemedText>
        </View>
      )}
      <View style={styles.productInfo}>
        <ThemedText variant="primary" style={styles.productName} numberOfLines={2}>
          {product.name}
        </ThemedText>
        <ThemedText variant="accent" style={styles.productPrice}>
          {product.price.toLocaleString()}원
        </ThemedText>
        {isOutOfStock && (
          <ThemedText variant="danger" style={styles.outOfStock} accessibilityRole="text">
            품절
          </ThemedText>
        )}
      </View>
    </Pressable>
  );
}

function GoodsSection() {
  const router = useRouter();
  const { data: products, isLoading, isError, refetch } = useProducts();

  if (isLoading) {
    return <LoadingView variant="skeleton" />;
  }

  if (isError) {
    return <ErrorView message="상품 목록을 불러오지 못했습니다." onRetry={() => void refetch()} />;
  }

  return (
    <FlatList
      data={products ?? []}
      keyExtractor={(item) => String(item.id)}
      numColumns={2}
      columnWrapperStyle={styles.productRow}
      renderItem={({ item }) => (
        <ProductCard
          product={item}
          onPress={() => router.push(ROUTES.product.detail(String(item.id)))}
        />
      )}
      ListEmptyComponent={<EmptyState message="등록된 상품이 없습니다." />}
      contentContainerStyle={styles.productListContent}
    />
  );
}

type TicketFilterChip = { label: string; value: EventStatus | undefined };

const TICKET_FILTER_CHIPS: TicketFilterChip[] = [
  { label: '전체', value: undefined },
  { label: '예정', value: 'SCHEDULED' },
  { label: '오픈', value: 'OPEN' },
  { label: '종료', value: 'CLOSED' },
];

function ticketStatusLabel(status: EventStatus): string {
  const map: Record<EventStatus, string> = {
    SCHEDULED: '예정',
    OPEN: '오픈',
    CLOSED: '종료',
  };
  return map[status];
}

function formatEventDate(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

interface EventCardProps {
  event: EventResponse;
}

function EventCard({ event }: EventCardProps) {
  const router = useRouter();
  const { tokens } = useTheme();
  const badgeBackground =
    event.status === 'OPEN'
      ? tokens.success
      : event.status === 'CLOSED'
        ? tokens.disabled
        : tokens.border;

  return (
    <Pressable
      style={[styles.eventCard, { backgroundColor: tokens.surface }]}
      accessibilityRole="button"
      accessibilityLabel={`${event.title} 경기 상세 보기`}
      onPress={() => router.push(ROUTES.event.detail(String(event.id)))}
    >
      <View style={styles.eventCardHeader}>
        <ThemedText variant="primary" style={styles.eventCardTitle} numberOfLines={1}>
          {event.title}
        </ThemedText>
        <View style={[styles.eventStatusBadge, { backgroundColor: badgeBackground }]}>
          <ThemedText variant="onAccent" style={styles.eventStatusBadgeText}>
            {ticketStatusLabel(event.status)}
          </ThemedText>
        </View>
      </View>
      <ThemedText variant="secondary" style={styles.eventCardVenue}>
        {event.venue}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.eventCardDate}>
        {formatEventDate(event.startsAt)}
      </ThemedText>
    </Pressable>
  );
}

function TicketsSection() {
  const { tokens } = useTheme();
  const [selectedStatus, setSelectedStatus] = useState<EventStatus | undefined>(undefined);
  const { data, isLoading, isError, refetch } = useEvents(0, 20, selectedStatus);

  return (
    <View style={styles.ticketsContainer}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.chipRow}
        contentContainerStyle={styles.chipRowContent}
        accessibilityRole="tablist"
        accessibilityLabel="경기 상태 필터"
      >
        {TICKET_FILTER_CHIPS.map((chip) => {
          const isSelected = selectedStatus === chip.value;
          return (
            <Pressable
              key={chip.label}
              style={[
                styles.chip,
                { backgroundColor: tokens.surface },
                isSelected && { backgroundColor: tokens.accent },
              ]}
              accessibilityRole="tab"
              accessibilityLabel={`${chip.label} 필터`}
              accessibilityState={{ selected: isSelected }}
              onPress={() => setSelectedStatus(chip.value)}
            >
              <ThemedText variant={isSelected ? 'onAccent' : 'secondary'} style={styles.chipText}>
                {chip.label}
              </ThemedText>
            </Pressable>
          );
        })}
      </ScrollView>

      {isLoading && <LoadingView variant="skeleton" />}

      {!isLoading && isError && (
        <ErrorView message="경기 목록을 불러올 수 없습니다." onRetry={() => void refetch()} />
      )}

      {!isLoading && !isError && (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <EventCard event={item} />}
          contentContainerStyle={styles.eventListContent}
          ListEmptyComponent={<EmptyState message="해당하는 경기가 없습니다." />}
        />
      )}
    </View>
  );
}

export default function StoreTabScreen() {
  const params = useLocalSearchParams<{ segment?: string }>();
  const [segment, setSegment] = useState<StoreSegment>(
    isStoreSegment(params.segment) ? params.segment : 'goods'
  );

  return (
    <ThemedView style={styles.container} background="background">
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        스토어
      </ThemedText>

      <View style={styles.segmentWrapper}>
        <SegmentedControl
          options={SEGMENT_OPTIONS}
          value={segment}
          onChange={(value) => setSegment(isStoreSegment(value) ? value : 'goods')}
        />
      </View>

      {segment === 'goods' ? <GoodsSection /> : <TicketsSection />}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 56,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  segmentWrapper: {
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  // 굿즈 섹션
  productRow: {
    justifyContent: 'space-between',
    paddingHorizontal: 8,
  },
  productListContent: {
    paddingBottom: 24,
  },
  productCard: {
    flex: 1,
    margin: 4,
    borderRadius: 10,
    overflow: 'hidden',
  },
  productImage: {
    height: 140,
    width: '100%',
  },
  productImagePlaceholder: {
    height: 140,
    alignItems: 'center',
    justifyContent: 'center',
  },
  productImageText: {
    fontSize: 12,
  },
  productInfo: {
    padding: 10,
  },
  productName: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 4,
  },
  productPrice: {
    fontSize: 14,
    fontWeight: '700',
  },
  outOfStock: {
    fontSize: 12,
    marginTop: 2,
  },
  // 티켓 섹션
  ticketsContainer: {
    flex: 1,
  },
  chipRow: {
    flexGrow: 0,
    marginBottom: 12,
  },
  chipRowContent: {
    paddingHorizontal: 16,
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
  },
  chipText: {
    fontSize: 14,
  },
  eventListContent: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  eventCard: {
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  eventCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  eventCardTitle: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    marginRight: 8,
  },
  eventStatusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  eventStatusBadgeText: {
    fontSize: 12,
    fontWeight: '600',
  },
  eventCardVenue: {
    fontSize: 14,
    marginBottom: 4,
  },
  eventCardDate: {
    fontSize: 13,
  },
});
