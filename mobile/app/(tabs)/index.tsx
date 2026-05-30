/**
 * 홈 화면 — 다가오는 경기 + 상품 추천.
 * GET /events, GET /products를 호출해 요약을 보여준다.
 */
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { getBeClient } from '../../api/be-client';

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
  const events = useQuery({ queryKey: ['home-events'], queryFn: fetchEvents });
  const products = useQuery({ queryKey: ['home-products'], queryFn: fetchProducts });

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.appTitle}>Sports App</Text>
      <Text style={styles.appSubtitle}>생활 체육 예약 플랫폼</Text>

      <Text style={styles.sectionTitle}>다가오는 경기</Text>
      {events.isLoading ? (
        <ActivityIndicator color="#007AFF" />
      ) : events.isError ? (
        <Text style={styles.error}>경기를 불러오지 못했습니다.</Text>
      ) : events.data && events.data.length > 0 ? (
        events.data.map((e) => (
          <View key={e.id} style={styles.card}>
            <Text style={styles.cardTitle}>{e.title}</Text>
            <Text style={styles.cardMeta}>
              {e.venue} · {formatDate(e.startsAt)} · {e.status}
            </Text>
          </View>
        ))
      ) : (
        <Text style={styles.empty}>등록된 경기가 없습니다.</Text>
      )}

      <Text style={styles.sectionTitle}>상품</Text>
      {products.isLoading ? (
        <ActivityIndicator color="#007AFF" />
      ) : products.isError ? (
        <Text style={styles.error}>상품을 불러오지 못했습니다.</Text>
      ) : products.data && products.data.length > 0 ? (
        products.data.map((p) => (
          <View key={p.id} style={styles.card}>
            <Text style={styles.cardTitle}>{p.name}</Text>
            <Text style={styles.cardMeta}>
              {p.category} · {p.price.toLocaleString('ko-KR')}원
            </Text>
          </View>
        ))
      ) : (
        <Text style={styles.empty}>등록된 상품이 없습니다.</Text>
      )}

      <StatusBar style="auto" />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  content: { padding: 20, paddingTop: 56 },
  appTitle: { fontSize: 26, fontWeight: 'bold', color: '#1C1C1E' },
  appSubtitle: { fontSize: 14, color: '#8E8E93', marginTop: 2, marginBottom: 24 },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1C1C1E',
    marginTop: 16,
    marginBottom: 10,
  },
  card: {
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
  },
  cardTitle: { fontSize: 16, fontWeight: '600', color: '#1C1C1E' },
  cardMeta: { fontSize: 13, color: '#8E8E93', marginTop: 4 },
  error: { color: '#FF3B30' },
  empty: { color: '#8E8E93' },
});
