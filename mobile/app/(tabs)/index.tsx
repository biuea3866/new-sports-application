/**
 * 홈 탭 — 다가오는 경기 5건 + 추천 상품 5건 + 근처 시설 요약
 * PIPELINE-TICKET: MO-03
 */
import {
  View,
  Text,
  ScrollView,
  ActivityIndicator,
  StyleSheet,
  FlatList,
} from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import {
  fetchUpcomingEvents,
  fetchRecommendedProducts,
  fetchNearbyFacilities,
} from '../../api/home';
import type { EventSummary, ProductSummary, FacilitySummary } from '../../api/home';

// 섹션 헤더 컴포넌트

interface SectionHeaderProps {
  title: string;
}

function SectionHeader({ title }: SectionHeaderProps) {
  return (
    <Text style={styles.sectionTitle} accessibilityRole="header">
      {title}
    </Text>
  );
}

// 이벤트 카드

interface EventCardProps {
  item: EventSummary;
}

function EventCard({ item }: EventCardProps) {
  const startDate = new Date(item.startAt).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
  });
  return (
    <View
      style={styles.card}
      accessible={true}
      accessibilityLabel={`경기: ${item.title}, ${startDate}, ${item.location}`}
    >
      <Text style={styles.cardTitle} numberOfLines={1}>
        {item.title}
      </Text>
      <Text style={styles.cardSub}>{startDate}</Text>
      <Text style={styles.cardSub} numberOfLines={1}>
        {item.location}
      </Text>
    </View>
  );
}

// 상품 카드

interface ProductCardProps {
  item: ProductSummary;
}

function ProductCard({ item }: ProductCardProps) {
  const priceText = item.price.toLocaleString('ko-KR') + '원';
  return (
    <View
      style={styles.card}
      accessible={true}
      accessibilityLabel={`상품: ${item.name}, ${priceText}`}
    >
      <Text style={styles.cardTitle} numberOfLines={1}>
        {item.name}
      </Text>
      <Text style={styles.cardPrice}>{priceText}</Text>
    </View>
  );
}

// 시설 카드

interface FacilityCardProps {
  item: FacilitySummary;
}

function FacilityCard({ item }: FacilityCardProps) {
  return (
    <View
      style={styles.card}
      accessible={true}
      accessibilityLabel={`시설: ${item.name}, ${item.address}`}
    >
      <Text style={styles.cardTitle} numberOfLines={1}>
        {item.name}
      </Text>
      <Text style={styles.cardSub} numberOfLines={1}>
        {item.address}
      </Text>
    </View>
  );
}

// 에러 뷰

interface ErrorViewProps {
  message: string;
}

function ErrorView({ message }: ErrorViewProps) {
  return (
    <View style={styles.errorContainer} accessible={true} accessibilityRole="alert">
      <Text style={styles.errorText}>{message}</Text>
    </View>
  );
}

// 메인 화면

export default function HomeScreen() {
  const {
    data: events,
    isLoading: eventsLoading,
    isError: eventsError,
  } = useQuery({
    queryKey: ['home', 'events'],
    queryFn: fetchUpcomingEvents,
  });

  const {
    data: products,
    isLoading: productsLoading,
    isError: productsError,
  } = useQuery({
    queryKey: ['home', 'products'],
    queryFn: fetchRecommendedProducts,
  });

  const {
    data: facilities,
    isLoading: facilitiesLoading,
    isError: facilitiesError,
  } = useQuery({
    queryKey: ['home', 'facilities'],
    queryFn: fetchNearbyFacilities,
  });

  const isLoading = eventsLoading || productsLoading || facilitiesLoading;

  if (isLoading) {
    return (
      <View style={styles.centered} accessible={true} accessibilityLabel="홈 화면 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" accessibilityLabel="로딩 중" />
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.contentContainer}
      accessibilityLabel="홈 화면"
    >
      <StatusBar style="auto" />

      <Text style={styles.screenTitle}>Sports App</Text>

      {/* 다가오는 경기 */}
      <SectionHeader title="다가오는 경기" />
      {eventsError ? (
        <ErrorView message="경기 정보를 불러오지 못했습니다." />
      ) : (
        <FlatList<EventSummary>
          data={events ?? []}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <EventCard item={item} />}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.horizontalList}
          scrollEnabled={false}
          ListEmptyComponent={<Text style={styles.emptyText}>예정된 경기가 없습니다.</Text>}
          accessibilityLabel="다가오는 경기 목록"
        />
      )}

      {/* 추천 상품 */}
      <SectionHeader title="추천 상품" />
      {productsError ? (
        <ErrorView message="상품 정보를 불러오지 못했습니다." />
      ) : (
        <FlatList<ProductSummary>
          data={products ?? []}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <ProductCard item={item} />}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.horizontalList}
          scrollEnabled={false}
          ListEmptyComponent={<Text style={styles.emptyText}>추천 상품이 없습니다.</Text>}
          accessibilityLabel="추천 상품 목록"
        />
      )}

      {/* 근처 시설 */}
      <SectionHeader title="근처 시설" />
      {facilitiesError ? (
        <ErrorView message="시설 정보를 불러오지 못했습니다." />
      ) : (
        <FlatList<FacilitySummary>
          data={facilities ?? []}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <FacilityCard item={item} />}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.horizontalList}
          scrollEnabled={false}
          ListEmptyComponent={<Text style={styles.emptyText}>근처 시설이 없습니다.</Text>}
          accessibilityLabel="근처 시설 목록"
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  contentContainer: {
    paddingTop: 60,
    paddingBottom: 32,
  },
  centered: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  screenTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1C1C1E',
    paddingHorizontal: 16,
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1C1C1E',
    paddingHorizontal: 16,
    marginBottom: 12,
    marginTop: 24,
  },
  horizontalList: {
    paddingHorizontal: 16,
    gap: 12,
  },
  card: {
    width: 160,
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 12,
  },
  cardTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  cardSub: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 2,
  },
  cardPrice: {
    fontSize: 13,
    fontWeight: '600',
    color: '#007AFF',
    marginTop: 4,
  },
  emptyText: {
    fontSize: 13,
    color: '#8E8E93',
    paddingVertical: 8,
  },
  errorContainer: {
    marginHorizontal: 16,
    padding: 12,
    backgroundColor: '#FFF2F2',
    borderRadius: 8,
  },
  errorText: {
    fontSize: 13,
    color: '#FF3B30',
  },
});
