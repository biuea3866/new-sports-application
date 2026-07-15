/**
 * 커뮤니티 탭 — 게시글|동아리 세그먼트 통합 화면 + 채팅 진입 아이콘.
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "화면 목록" A-P5,
 * "텍스트 와이어프레임" A-P5(토스 카테고리 칩 — 가로 스크롤, 선택 시 accent 1곳),
 * "화면별 4상태 표"(종목 0건은 정상 empty). `community.post.enabled` 플래그로 종목 필터
 * 진입점을 게이트한다(design-fe-app "Release Scenario").
 *
 * 사용자 피드백 "커뮤니티 = 기존 커뮤니티(게시글) + 동아리를 세그먼트 컨트롤로 통합" —
 * `chat.community.enabled` 플래그가 OFF면 세그먼트 컨트롤 자체를 숨기고 게시글만
 * 노출한다(예전 동아리 탭 `href: null` 게이팅과 동일한 정책을 세그먼트 단위로 유지).
 * "채팅은 탭에서 제거 → 홈·커뮤니티 화면 상단 우측 아이콘으로 진입" — `ChatEntryButton`을
 * 헤더에 배치한다.
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import type { CommunityResponse, SportCategory } from '../../api/community-types';
import { ChatEntryButton } from '../../components/common/ChatEntryButton';
import { PostCard } from '../../components/community/PostCard';
import { SportCategoryChips } from '../../components/community/SportCategoryChips';
import {
  Card,
  EmptyState,
  ErrorView,
  LoadingView,
  SegmentedControl,
  ThemedText,
  ThemedView,
} from '../../components/ui';
import { getSportCategoryDisplay, getVisibilityLabel } from '../../lib/community-format';
import { isFeatureEnabled } from '../../lib/feature-flags';
import { useCommunities } from '../../lib/useCommunity';
import { usePosts } from '../../lib/usePosts';
import { useTheme } from '../../theme/useTheme';

const POSTS_EMPTY_MESSAGE = '게시글이 없어요';
const CATEGORY_EMPTY_MESSAGE = '이 종목 글이 아직 없어요';
const POSTS_ERROR_MESSAGE = '게시글을 불러오지 못했습니다.';
const CLUBS_EMPTY_MESSAGE = '아직 동아리가 없어요. 첫 동아리를 만들어보세요';
const CLUBS_ERROR_MESSAGE = '동아리 목록을 불러오지 못했습니다.';

type CommunitySegment = 'posts' | 'clubs';

const SEGMENT_OPTIONS = [
  { label: '게시글', value: 'posts' },
  { label: '동아리', value: 'clubs' },
];

function isCommunitySegment(value: unknown): value is CommunitySegment {
  return value === 'posts' || value === 'clubs';
}

function PostsSection() {
  const router = useRouter();
  const isCategoryFilterEnabled = isFeatureEnabled('community.post.enabled');

  const [sportCategory, setSportCategory] = useState<SportCategory | null>(null);
  const { data, isLoading, isError, refetch } = usePosts(
    0,
    20,
    sportCategory !== null ? { sportCategory } : undefined
  );
  const posts = data?.content ?? [];

  return (
    <View style={styles.sectionContainer}>
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
        <ErrorView message={POSTS_ERROR_MESSAGE} onRetry={() => void refetch()} />
      )}

      {!isLoading && !isError && (
        <FlatList
          data={posts}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <PostCard post={item} onPress={() => router.push(`/community/${item.id}`)} />
          )}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <EmptyState
              message={sportCategory !== null ? CATEGORY_EMPTY_MESSAGE : POSTS_EMPTY_MESSAGE}
            />
          }
        />
      )}
    </View>
  );
}

interface ClubCardProps {
  community: CommunityResponse;
}

function ClubCard({ community }: ClubCardProps) {
  const router = useRouter();
  const sportCategory = getSportCategoryDisplay(community.sportCategory);
  const visibilityLabel = getVisibilityLabel(community.visibility);

  return (
    <Card
      testID={`community-card-${community.id}`}
      onPress={() => router.push(`/communities/${community.id}`)}
      accessibilityLabel={`${community.name}, ${visibilityLabel}, 멤버 ${community.memberCount}명`}
      style={styles.clubCard}
    >
      <View style={styles.clubCardHeaderRow}>
        <ThemedText variant="primary" style={styles.clubCardTitle} numberOfLines={1}>
          {sportCategory.emoji} {community.name}
        </ThemedText>
        <ThemedText variant="secondary" style={styles.clubCardMeta}>
          {visibilityLabel} · {community.memberCount}명
        </ThemedText>
      </View>
      {community.description ? (
        <ThemedText variant="secondary" style={styles.clubCardDescription} numberOfLines={1}>
          {community.description}
        </ThemedText>
      ) : null}
    </Card>
  );
}

function ClubsSection() {
  const router = useRouter();
  const { tokens } = useTheme();
  const [keyword, setKeyword] = useState('');
  const trimmedKeyword = keyword.trim();
  const { data, isLoading, isError, refetch } = useCommunities(
    trimmedKeyword.length > 0 ? trimmedKeyword : undefined
  );

  const communities = data ?? [];

  return (
    <View style={styles.sectionContainer}>
      <TextInput
        value={keyword}
        onChangeText={setKeyword}
        placeholder="종목·이름 검색"
        placeholderTextColor={tokens.textTertiary}
        style={[
          styles.searchInput,
          {
            backgroundColor: tokens.surface,
            color: tokens.textPrimary,
            borderColor: tokens.border,
          },
        ]}
        accessibilityLabel="종목·이름 검색"
      />

      <View style={styles.clubListBody}>
        {isLoading ? (
          <LoadingView variant="skeleton" />
        ) : isError ? (
          <ErrorView message={CLUBS_ERROR_MESSAGE} onRetry={() => void refetch()} />
        ) : communities.length === 0 ? (
          <EmptyState message={CLUBS_EMPTY_MESSAGE} />
        ) : (
          <FlatList
            data={communities}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => <ClubCard community={item} />}
            contentContainerStyle={styles.list}
          />
        )}
      </View>

      <Pressable
        style={[styles.floatingCta, { backgroundColor: tokens.accent }]}
        onPress={() => router.push('/communities/new')}
        accessibilityRole="button"
        accessibilityLabel="동아리 개설"
      >
        <ThemedText variant="onAccent" style={styles.floatingCtaLabel}>
          + 개설
        </ThemedText>
      </Pressable>
    </View>
  );
}

export default function CommunityTabScreen() {
  const { tokens } = useTheme();
  const router = useRouter();
  const isClubsSegmentEnabled = isFeatureEnabled('chat.community.enabled');

  const [segment, setSegment] = useState<CommunitySegment>('posts');

  const handleNewPost = () => {
    router.push('/community/new');
  };

  return (
    <ThemedView style={styles.container} background="background">
      <View style={styles.header}>
        <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
          커뮤니티
        </ThemedText>
        <View style={styles.headerActions}>
          {segment === 'posts' ? (
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
          ) : null}
          <ChatEntryButton />
        </View>
      </View>

      {isClubsSegmentEnabled && (
        <View style={styles.segmentWrapper}>
          <SegmentedControl
            options={SEGMENT_OPTIONS}
            value={segment}
            onChange={(value) => setSegment(isCommunitySegment(value) ? value : 'posts')}
          />
        </View>
      )}

      {segment === 'posts' || !isClubsSegmentEnabled ? <PostsSection /> : <ClubsSection />}
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
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
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
  segmentWrapper: {
    paddingHorizontal: 16,
    paddingBottom: 12,
  },
  sectionContainer: {
    flex: 1,
  },
  chipsWrapper: {
    paddingHorizontal: 16,
    paddingBottom: 12,
  },
  list: {
    padding: 16,
  },
  // 동아리 섹션
  searchInput: {
    marginHorizontal: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 15,
  },
  clubListBody: {
    flex: 1,
  },
  clubCard: {
    marginBottom: 12,
  },
  clubCardHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  clubCardTitle: {
    fontSize: 16,
    fontWeight: '600',
    flex: 1,
    marginRight: 8,
  },
  clubCardMeta: {
    fontSize: 13,
  },
  clubCardDescription: {
    fontSize: 13,
    marginTop: 4,
  },
  floatingCta: {
    position: 'absolute',
    right: 20,
    bottom: 32,
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 24,
  },
  floatingCtaLabel: {
    fontSize: 15,
    fontWeight: '700',
  },
});
