/**
 * 게시글 상세 + 댓글 화면 — A-P4 (전역·모임 게시글 공용)
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "화면 목록" A-P4,
 * "화면별 4상태 표"(403→잠금, 404→"없는 게시글", 댓글 0건→"첫 댓글을 남겨보세요"),
 * "상태관리 설계"(댓글 작성만 낙관적 업데이트 — `lib/usePosts.ts#useAddComment`에서 처리).
 */
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';

import { formatAuthor, formatRelativeTime } from '../../lib/post-format';
import { isForbiddenError, isNotFoundError } from '../../lib/http-error';
import { useAddComment, useComments, usePost } from '../../lib/usePosts';
import {
  Button,
  EmptyState,
  ErrorView,
  LoadingView,
  ThemedText,
  ThemedView,
} from '../../components/ui';
import { useTheme } from '../../theme/useTheme';

const LOCKED_MESSAGE = '🔒 멤버만 볼 수 있어요';
const NOT_FOUND_MESSAGE = '없는 게시글이에요';
const GENERIC_ERROR_MESSAGE = '게시글을 불러오지 못했어요';
const EMPTY_COMMENTS_MESSAGE = '첫 댓글을 남겨보세요';

export default function CommunityDetailScreen() {
  const { tokens } = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const postId = Number(id ?? '0');

  const postQuery = usePost(postId);
  const commentsQuery = useComments(postId);
  const addCommentMutation = useAddComment(postId);
  const [commentText, setCommentText] = useState('');

  const isLocked = isForbiddenError(postQuery.error);
  const isNotFound = isNotFoundError(postQuery.error);
  const post = postQuery.data;
  const comments = commentsQuery.data?.content ?? [];

  function handleAddComment() {
    const trimmed = commentText.trim();
    if (trimmed.length === 0) {
      return;
    }
    addCommentMutation.mutate(trimmed, {
      onSuccess: () => setCommentText(''),
    });
  }

  if (postQuery.isLoading) {
    return (
      <ThemedView style={styles.centered} background="background">
        <LoadingView />
      </ThemedView>
    );
  }

  if (isLocked) {
    return (
      <ThemedView style={styles.centered} background="background">
        <EmptyState message={LOCKED_MESSAGE} />
      </ThemedView>
    );
  }

  if (isNotFound) {
    return (
      <ThemedView style={styles.centered} background="background">
        <EmptyState message={NOT_FOUND_MESSAGE} />
      </ThemedView>
    );
  }

  if (postQuery.isError || post === undefined) {
    return (
      <ThemedView style={styles.centered} background="background">
        <ErrorView message={GENERIC_ERROR_MESSAGE} onRetry={() => postQuery.refetch()} />
      </ThemedView>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView style={styles.scroll}>
        <View style={styles.content}>
          {post.type === 'NOTICE' && (
            <View style={[styles.noticeBadge, { backgroundColor: tokens.accent }]}>
              <ThemedText variant="onAccent" style={styles.noticeBadgeLabel}>
                📌 공지
              </ThemedText>
            </View>
          )}
          <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
            {post.title}
          </ThemedText>
          <View style={styles.meta}>
            <ThemedText variant="secondary" accessibilityLabel={`작성자 ID ${post.userId}`}>
              {formatAuthor(post.userId)}
            </ThemedText>
            <ThemedText variant="secondary">{formatRelativeTime(post.createdAt)}</ThemedText>
          </View>
          <View style={[styles.divider, { backgroundColor: tokens.border }]} />
          <ThemedText variant="primary" style={styles.body} accessibilityRole="text">
            {post.content}
          </ThemedText>

          <View style={[styles.commentsSection, { borderTopColor: tokens.border }]}>
            <ThemedText variant="primary" style={styles.commentsTitle}>
              {`댓글 ${comments.length}`}
            </ThemedText>

            {commentsQuery.isLoading && <LoadingView />}

            {!commentsQuery.isLoading && commentsQuery.isError && (
              <ErrorView
                message="댓글을 불러오지 못했어요"
                onRetry={() => commentsQuery.refetch()}
              />
            )}

            {!commentsQuery.isLoading && !commentsQuery.isError && comments.length === 0 && (
              <EmptyState message={EMPTY_COMMENTS_MESSAGE} />
            )}

            {!commentsQuery.isLoading &&
              !commentsQuery.isError &&
              comments.map((comment) => (
                <View key={comment.id} style={styles.commentItem}>
                  <ThemedText variant="secondary" style={styles.commentMeta}>
                    {`${formatAuthor(comment.userId)} · ${formatRelativeTime(comment.createdAt)}`}
                  </ThemedText>
                  <ThemedText variant="primary" style={styles.commentContent}>
                    {comment.content}
                  </ThemedText>
                </View>
              ))}
          </View>
        </View>
      </ScrollView>

      <View style={[styles.commentInputRow, { borderTopColor: tokens.border }]}>
        <TextInput
          style={[styles.commentInput, { borderColor: tokens.border, color: tokens.textPrimary }]}
          value={commentText}
          onChangeText={setCommentText}
          placeholder="댓글을 입력하세요"
          placeholderTextColor={tokens.textTertiary}
          accessibilityLabel="댓글 입력"
        />
        <View style={styles.commentSubmitButton}>
          <Button
            label="등록"
            onPress={handleAddComment}
            disabled={commentText.trim().length === 0 || addCommentMutation.isPending}
          />
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scroll: {
    flex: 1,
  },
  content: {
    padding: 20,
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
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 12,
  },
  meta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  divider: {
    height: 1,
    marginBottom: 20,
  },
  body: {
    fontSize: 16,
    lineHeight: 24,
  },
  commentsSection: {
    marginTop: 32,
    paddingTop: 20,
    borderTopWidth: 1,
  },
  commentsTitle: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 12,
  },
  commentItem: {
    marginBottom: 16,
  },
  commentMeta: {
    fontSize: 12,
    marginBottom: 4,
  },
  commentContent: {
    fontSize: 15,
    lineHeight: 21,
  },
  commentInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: 1,
    gap: 8,
  },
  commentInput: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
  },
  commentSubmitButton: {
    minWidth: 72,
  },
});
