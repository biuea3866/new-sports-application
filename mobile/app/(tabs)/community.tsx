/**
 * 커뮤니티 탭 — 게시글 목록
 */
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { usePosts } from '../../lib/usePosts';
import type { PostResponse } from '../../api/types';

function formatDate(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}

interface PostCardProps {
  post: PostResponse;
  onPress: () => void;
}

function PostCard({ post, onPress }: PostCardProps) {
  return (
    <Pressable
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`게시글: ${post.title}`}
    >
      <Text style={styles.cardTitle} numberOfLines={2}>
        {post.title}
      </Text>
      <View style={styles.cardMeta}>
        <Text style={styles.cardUserId} accessibilityLabel={`작성자 ID ${post.userId}`}>
          {`사용자 ${post.userId}`}
        </Text>
        <Text style={styles.cardDate}>{formatDate(post.createdAt)}</Text>
      </View>
    </Pressable>
  );
}

export default function CommunityTabScreen() {
  const router = useRouter();
  const { data, isLoading, isError } = usePosts(0, 20);

  const handleNewPost = () => {
    router.push('/community/new');
  };

  const handlePostPress = (id: number) => {
    router.push(`/community/${id}`);
  };

  return (
    <View style={styles.container} accessible={true} accessibilityLabel="커뮤니티 화면">
      <View style={styles.header}>
        <Text style={styles.title} accessibilityRole="header">
          커뮤니티
        </Text>
        <Pressable
          style={styles.newButton}
          onPress={handleNewPost}
          accessibilityRole="button"
          accessibilityLabel="게시글 작성"
        >
          <Text style={styles.newButtonText}>+</Text>
        </Pressable>
      </View>

      {isLoading && (
        <View style={styles.centered} accessibilityLabel="게시글 목록 로딩 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && (
        <View style={styles.centered} accessibilityLabel="게시글 목록 오류">
          <Text style={styles.errorText} accessibilityRole="alert">
            게시글을 불러오지 못했습니다.
          </Text>
        </View>
      )}

      {!isLoading && !isError && (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <PostCard post={item} onPress={() => handlePostPress(item.id)} />
          )}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <View style={styles.centered}>
              <Text style={styles.emptyText} accessibilityRole="text">
                게시글이 없습니다.
              </Text>
            </View>
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 56,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
  },
  newButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#007AFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  newButtonText: {
    fontSize: 24,
    color: '#fff',
    lineHeight: 30,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  list: {
    padding: 16,
  },
  card: {
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  cardMeta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  cardUserId: {
    fontSize: 13,
    color: '#8E8E93',
  },
  cardDate: {
    fontSize: 13,
    color: '#8E8E93',
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
  emptyText: {
    fontSize: 14,
    color: '#8E8E93',
  },
});
