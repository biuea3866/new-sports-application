/**
 * 커뮤니티(동아리) 목록 화면 — S3
 *
 * 근거: FE-11 티켓, `20260704-채팅시스템고도화-design-fe-app.md` S3.
 * 토스 패턴: 검색 + 카드 리스트, 하단 플로팅 단일 CTA(개설).
 *
 * `useCommunities`(FE-07)로 공개 커뮤니티를 검색·조회한다. 카드 탭 시 상세(`communities/{id}`,
 * S5)로, 플로팅 CTA 탭 시 개설(`communities/new`, S4)로 이동한다.
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { router } from 'expo-router';

import { Card, EmptyState, ErrorView, LoadingView, ThemedText } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';
import { useCommunities } from '../../lib/useCommunity';
import { getSportCategoryDisplay, getVisibilityLabel } from '../../lib/community-format';
import type { CommunityResponse } from '../../api/community-types';

interface CommunityCardProps {
  community: CommunityResponse;
}

function CommunityCard({ community }: CommunityCardProps) {
  const sportCategory = getSportCategoryDisplay(community.sportCategory);
  const visibilityLabel = getVisibilityLabel(community.visibility);

  return (
    <Card
      testID={`community-card-${community.id}`}
      onPress={() => router.push(`/communities/${community.id}`)}
      accessibilityLabel={`${community.name}, ${visibilityLabel}, 멤버 ${community.memberCount}명`}
      style={styles.card}
    >
      <View style={styles.cardHeaderRow}>
        <ThemedText variant="primary" style={styles.cardTitle} numberOfLines={1}>
          {sportCategory.emoji} {community.name}
        </ThemedText>
        <ThemedText variant="secondary" style={styles.cardMeta}>
          {visibilityLabel} · {community.memberCount}명
        </ThemedText>
      </View>
      {community.description ? (
        <ThemedText variant="secondary" style={styles.cardDescription} numberOfLines={1}>
          {community.description}
        </ThemedText>
      ) : null}
    </Card>
  );
}

export default function CommunitiesListScreen() {
  const { tokens } = useTheme();
  const [keyword, setKeyword] = useState('');
  const trimmedKeyword = keyword.trim();
  const { data, isLoading, isError, refetch } = useCommunities(
    trimmedKeyword.length > 0 ? trimmedKeyword : undefined
  );

  const communities = data ?? [];

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        동아리
      </ThemedText>
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

      <View style={styles.body}>
        {isLoading ? (
          <LoadingView variant="skeleton" />
        ) : isError ? (
          <ErrorView message="동아리 목록을 불러오지 못했습니다." onRetry={() => void refetch()} />
        ) : communities.length === 0 ? (
          <EmptyState message="아직 동아리가 없어요. 첫 동아리를 만들어보세요" />
        ) : (
          <FlatList
            data={communities}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => <CommunityCard community={item} />}
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 16,
  },
  searchInput: {
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 15,
    marginBottom: 16,
  },
  body: {
    flex: 1,
  },
  list: {
    paddingBottom: 96,
  },
  card: {
    marginBottom: 12,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    flex: 1,
    marginRight: 8,
  },
  cardMeta: {
    fontSize: 13,
  },
  cardDescription: {
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
