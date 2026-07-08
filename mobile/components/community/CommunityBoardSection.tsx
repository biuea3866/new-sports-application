/**
 * CommunityBoardSection — 모임 상세 게시판 섹션(A-P1/A-P2).
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-P1/A-P2(토스 피드 패턴), "화면별 4상태 표".
 * `GET /communities/{communityId}/posts`는 PRIVATE 비멤버에게 403을 반환하므로(서버 인가),
 * 이 섹션은 UI 게이팅에 의존하지 않고 403 응답을 그대로 잠금 상태로 렌더한다
 * (communities/[id].tsx 멤버 목록 섹션과 동일 패턴).
 */
import { FlatList, StyleSheet, View } from 'react-native';

import { useCommunityPosts } from '../../lib/usePosts';
import { isForbiddenError } from '../../lib/http-error';
import { Button, EmptyState, ErrorView, LoadingView } from '../ui';
import { PostCard } from './PostCard';

const LOCKED_MESSAGE = '🔒 멤버만 볼 수 있어요';
const EMPTY_MESSAGE = '첫 글을 남겨보세요';
const ERROR_MESSAGE = '게시글 목록을 불러오지 못했어요';

export interface CommunityBoardSectionProps {
  communityId: number;
  /** ACTIVE 멤버(멤버·방장)만 true — 글쓰기 CTA 노출 여부. */
  canWrite: boolean;
  onCreatePost: () => void;
  onPostPress: (postId: number) => void;
}

export function CommunityBoardSection({
  communityId,
  canWrite,
  onCreatePost,
  onPostPress,
}: CommunityBoardSectionProps) {
  const { data, isLoading, isError, error, refetch } = useCommunityPosts(communityId);
  const isLocked = isForbiddenError(error);
  const posts = data?.content ?? [];

  return (
    <View style={styles.container}>
      {canWrite && (
        <View style={styles.ctaArea}>
          <Button label="+ 글쓰기" onPress={onCreatePost} />
        </View>
      )}

      {isLoading && <LoadingView variant="skeleton" />}

      {!isLoading && isLocked && <EmptyState message={LOCKED_MESSAGE} />}

      {!isLoading && !isLocked && isError && (
        <ErrorView message={ERROR_MESSAGE} onRetry={() => void refetch()} />
      )}

      {!isLoading && !isLocked && !isError && posts.length === 0 && (
        <EmptyState message={EMPTY_MESSAGE} />
      )}

      {!isLoading && !isLocked && !isError && posts.length > 0 && (
        <FlatList
          data={posts}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <PostCard post={item} onPress={() => onPostPress(item.id)} />}
          scrollEnabled={false}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: 8,
  },
  ctaArea: {
    marginBottom: 16,
  },
});
