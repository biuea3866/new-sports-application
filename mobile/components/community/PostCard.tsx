/**
 * PostCard — 게시글 목록(A-P2 모임 게시판·A-P5 전역 목록 공용) 카드.
 * NOTICE 타입은 상단에 공지 배지를 표시한다(design-fe-app.md "텍스트 와이어프레임" A-P1/A-P2).
 * 색은 useTheme() 토큰만 경유한다.
 */
import { StyleSheet, View } from 'react-native';

import type { PostResponse } from '../../api/types';
import { formatAuthor, formatRelativeTime } from '../../lib/post-format';
import { Card, ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';

export interface PostCardProps {
  post: PostResponse;
  onPress: () => void;
}

export function PostCard({ post, onPress }: PostCardProps) {
  const { tokens } = useTheme();
  const isNotice = post.type === 'NOTICE';
  const metaLine = `${formatAuthor(post.userId)} · ${formatRelativeTime(post.createdAt)}`;

  return (
    <Card
      testID={`post-card-${post.id}`}
      onPress={onPress}
      accessibilityLabel={`게시글: ${post.title}${isNotice ? ', 공지' : ''}`}
      style={styles.card}
    >
      {isNotice && (
        <View style={[styles.noticeBadge, { backgroundColor: tokens.accent }]}>
          <ThemedText variant="onAccent" style={styles.noticeBadgeLabel}>
            📌 공지
          </ThemedText>
        </View>
      )}
      <ThemedText variant="primary" style={styles.title} numberOfLines={2}>
        {post.title}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.meta}>
        {metaLine}
      </ThemedText>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 12,
  },
  noticeBadge: {
    alignSelf: 'flex-start',
    marginBottom: 8,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  noticeBadgeLabel: {
    fontSize: 11,
    fontWeight: '700',
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
  },
  meta: {
    fontSize: 13,
    marginTop: 6,
  },
});
