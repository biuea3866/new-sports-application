/**
 * 홈 화면 — 다가오는 경기 + 상품 추천 + 통합 검색 진입점(FE-11) + 채팅 진입 아이콘.
 * GET /events, GET /products를 호출해 요약을 보여준다.
 *
 * 통합 검색 진입점은 `catalog.enabled` 플래그로 게이팅한다(BE 파사드 API 준비 전 숨김,
 * `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "Release Scenario").
 *
 * 사용자 피드백 "다가오는 경기를 어디서 보는지 모르겠다" — 경기 카드를 탭하면
 * 스토어 탭을 `?segment=tickets`로 열어 티켓 세그먼트로 바로 이동한다(`(tabs)/store.tsx`).
 * "채팅은 탭에서 제거 → 홈 화면 상단 우측 아이콘으로 진입" — `ChatEntryButton`을 배치한다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { ScrollView, StyleSheet, ActivityIndicator, Pressable, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { getBeClient } from '../../api/be-client';
import { ChatEntryButton } from '../../components/common/ChatEntryButton';
import { ListItem, ThemedText } from '../../components/ui';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { ROUTES } from '../../lib/navigation';
import { useTheme } from '../../theme/useTheme';

interface EventItem {
  id: number;
  title: string;
  venue: string;
  startsAt: string;
  status: string;
}
interface ProductItem {
  id: number;
  name: string;
  category: string;
  price: number;
}
interface Page<T> {
  content: T[];
}

async function fetchEvents(): Promise<EventItem[]> {
  const res = await getBeClient().get<Page<EventItem>>('/events?page=0&size=5');
  return res.data.content;
}
async function fetchProducts(): Promise<ProductItem[]> {
  const res = await getBeClient().get<Page<ProductItem>>('/products?page=0&size=5');
  return res.data.content;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(
    d.getDate()
  ).padStart(2, '0')}`;
}

export default function HomeScreen() {
  const router = useRouter();
  const { tokens } = useTheme();
  const events = useQuery({ queryKey: ['home-events'], queryFn: fetchEvents });
  const products = useQuery({ queryKey: ['home-products'], queryFn: fetchProducts });
  const isCatalogEnabled = isFeatureEnabled('catalog.enabled');

  const handleUpcomingEventPress = () => {
    router.push(ROUTES.tabs.storeTickets);
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: tokens.background }]}
      contentContainerStyle={styles.content}
    >
      <View style={styles.header}>
        <View>
          <ThemedText variant="primary" style={styles.appTitle}>
            Sports App
          </ThemedText>
          <ThemedText variant="secondary" style={styles.appSubtitle}>
            생활 체육 예약 플랫폼
          </ThemedText>
        </View>
        <ChatEntryButton />
      </View>

      {isCatalogEnabled ? (
        <View style={styles.entryPoint}>
          <ListItem title="통합 검색" onPress={() => router.push(ROUTES.catalog)} />
        </View>
      ) : null}

      <ThemedText variant="primary" style={styles.sectionTitle}>
        다가오는 경기
      </ThemedText>
      {events.isLoading ? (
        <ActivityIndicator color={tokens.accent} />
      ) : events.isError ? (
        <ThemedText variant="danger" style={styles.error}>
          경기를 불러오지 못했습니다.
        </ThemedText>
      ) : events.data && events.data.length > 0 ? (
        events.data.map((e) => (
          <Pressable
            key={e.id}
            style={[styles.card, { borderColor: tokens.border }]}
            onPress={handleUpcomingEventPress}
            accessibilityRole="button"
            accessibilityLabel={`${e.title}, 스토어 티켓 탭으로 이동`}
          >
            <ThemedText variant="primary" style={styles.cardTitle}>
              {e.title}
            </ThemedText>
            <ThemedText variant="secondary" style={styles.cardMeta}>
              {e.venue} · {formatDate(e.startsAt)} · {e.status}
            </ThemedText>
          </Pressable>
        ))
      ) : (
        <ThemedText variant="secondary" style={styles.empty}>
          등록된 경기가 없습니다.
        </ThemedText>
      )}

      <ThemedText variant="primary" style={styles.sectionTitle}>
        상품
      </ThemedText>
      {products.isLoading ? (
        <ActivityIndicator color={tokens.accent} />
      ) : products.isError ? (
        <ThemedText variant="danger" style={styles.error}>
          상품을 불러오지 못했습니다.
        </ThemedText>
      ) : products.data && products.data.length > 0 ? (
        products.data.map((p) => (
          <Pressable
            key={p.id}
            style={[styles.card, { borderColor: tokens.border }]}
            onPress={() => router.push(ROUTES.product.detail(String(p.id)))}
            accessibilityRole="button"
            accessibilityLabel={`${p.name}, ${p.price.toLocaleString('ko-KR')}원, 상품 상세로 이동`}
          >
            <ThemedText variant="primary" style={styles.cardTitle}>
              {p.name}
            </ThemedText>
            <ThemedText variant="secondary" style={styles.cardMeta}>
              {p.category} · {p.price.toLocaleString('ko-KR')}원
            </ThemedText>
          </Pressable>
        ))
      ) : (
        <ThemedText variant="secondary" style={styles.empty}>
          등록된 상품이 없습니다.
        </ThemedText>
      )}

      <StatusBar style="auto" />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  entryPoint: { marginBottom: 8 },
  container: { flex: 1 },
  content: { padding: 20, paddingTop: 56 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  appTitle: { fontSize: 26, fontWeight: 'bold' },
  appSubtitle: { fontSize: 14, marginTop: 2 },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginTop: 16,
    marginBottom: 10,
  },
  card: {
    borderWidth: 1,
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
  },
  cardTitle: { fontSize: 16, fontWeight: '600' },
  cardMeta: { fontSize: 13, marginTop: 4 },
  error: {},
  empty: {},
});
