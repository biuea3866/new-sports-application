/**
 * 커뮤니티 탭 — 전역 게시글 목록 + 종목별 필터 (A-P5)
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "화면 목록" A-P5,
 * "텍스트 와이어프레임" A-P5(토스 카테고리 칩 — 가로 스크롤, 선택 시 accent 1곳),
 * "화면별 4상태 표"(종목 0건은 정상 empty). `community.post.enabled` 플래그로 종목 필터
 * 진입점을 게이트한다(design-fe-app "Release Scenario").
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import type { SportCategory } from '../../api/community-types';
import { PostCard } from '../../components/community/PostCard';
import { SportCategoryChips } from '../../components/community/SportCategoryChips';
import { EmptyState, ErrorView, LoadingView, ThemedText, ThemedView } from '../../components/ui';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { usePosts } from '../../lib/usePosts';
import { useTheme } from '../../theme/useTheme';

const EMPTY_MESSAGE = '게시글이 없어요';
const CATEGORY_EMPTY_MESSAGE = '이 종목 글이 아직 없어요';
const ERROR_MESSAGE = '게시글을 불러오지 못했습니다.';

export default function CommunityTabScreen() {
  const { tokens } = useTheme();
  const router = useRouter();
  const isCategoryFilterEnabled = isFeatureEnabled('community.post.enabled');

  const [sportCategory, setSportCategory] = useState<SportCategory | null>(null);
  const { data, isLoading, isError, refetch } = usePosts(
    0,
    20,
    sportCategory !== null ? { sportCategory } : undefined
  );
  const posts = data?.content ?? [];

  const handleNewPost = () => {
    router.push('/community/new');
  };

  const handlePostPress = (id: number) => {
    router.push(`/community/${id}`);
  };

  return (
    <ThemedView style={styles.container} background="background">
      <View style={styles.header}>
        <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
          커뮤니티
        </ThemedText>
        <Pressable
          style={[styles.newButton, { backgroundColor: tokens.accent }]}
          onPress={handleNewPost}
          accessibilityRole="button"
          accessibilityLabel="게시글 작성"
        >
          <ThemedText variant="onAccent" style={styles.newButtonText}>
            +
          </ThemedText>
        </Pressable>
      </View>

      {isCategoryFilterEnabled && (
        <View style={styles.chipsWrapper}>
          <SportCategoryChips
            selected={sportCategory}
            onSelect={setSportCategory}
            allLabel="전체"
          />
        </View>
      )}

      {isLoading && <LoadingView variant="skeleton" />}

      {!isLoading && isError && (
        <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
      )}

      {!isLoading && !isError && (
        <FlatList
          data={posts}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <PostCard post={item} onPress={() => handlePostPress(item.id)} />
          )}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <EmptyState message={sportCategory !== null ? CATEGORY_EMPTY_MESSAGE : EMPTY_MESSAGE} />
          }
        />
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 56,
    paddingBottom: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  newButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  newButtonText: {
    fontSize: 24,
    lineHeight: 30,
  },
  chipsWrapper: {
    paddingHorizontal: 16,
    paddingBottom: 12,
  },
  list: {
    padding: 16,
  },
});
