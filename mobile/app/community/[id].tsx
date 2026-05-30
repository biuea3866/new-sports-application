/**
 * 커뮤니티 게시글 상세 화면
 */
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { usePost } from '../../lib/usePosts';

function formatDate(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}

export default function CommunityDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const postId = Number(id ?? '0');
  const { data: post, isLoading, isError } = usePost(postId);

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="게시글 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError || post === undefined) {
    return (
      <View style={styles.centered} accessibilityLabel="게시글 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          게시글을 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      accessible={true}
      accessibilityLabel="게시글 상세 화면"
    >
      <View style={styles.content}>
        <Text style={styles.title} accessibilityRole="header">
          {post.title}
        </Text>
        <View style={styles.meta}>
          <Text style={styles.metaText} accessibilityLabel={`작성자 ID ${post.userId}`}>
            {`사용자 ${post.userId}`}
          </Text>
          <Text style={styles.metaText}>{formatDate(post.createdAt)}</Text>
        </View>
        <View style={styles.divider} />
        <Text style={styles.body} accessibilityRole="text">
          {post.content}
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
  content: {
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 12,
  },
  meta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  metaText: {
    fontSize: 13,
    color: '#8E8E93',
  },
  divider: {
    height: 1,
    backgroundColor: '#E5E5EA',
    marginBottom: 20,
  },
  body: {
    fontSize: 16,
    color: '#1C1C1E',
    lineHeight: 24,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
});
