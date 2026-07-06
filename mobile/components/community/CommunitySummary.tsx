/**
 * CommunitySummary — 커뮤니티 상단 요약 + 역할별 단일 주요 CTA(토스 패턴).
 * 비멤버: 가입하기 / 승인 대기: 비활성 CTA / 멤버·방장: 채팅 입장.
 * 근거: design-fe-app.md S5 와이어프레임·"화면별 상태 표".
 */
import { View, StyleSheet } from 'react-native';

import type { CommunityResponse } from '../../api/community-types';
import { Button, Card, ThemedText } from '../ui';
import type { ViewerMembership } from './communityRole';

const VISIBILITY_LABEL: Record<CommunityResponse['visibility'], string> = {
  PUBLIC: '공개',
  PRIVATE: '비공개',
};

const SPORT_CATEGORY_LABEL: Record<CommunityResponse['sportCategory'], string> = {
  SOCCER: '축구',
  BASKETBALL: '농구',
  BASEBALL: '야구',
  TENNIS: '테니스',
  BADMINTON: '배드민턴',
  GOLF: '골프',
  RUNNING: '러닝',
  CYCLING: '사이클링',
  SWIMMING: '수영',
  HIKING: '등산',
  YOGA: '요가',
  ETC: '기타',
};

export interface CommunitySummaryProps {
  community: CommunityResponse;
  viewer: ViewerMembership;
  onJoin: () => void;
  onEnterChat: () => void;
  isJoinPending: boolean;
}

function noop(): void {
  // 승인 대기 중 CTA는 비활성 상태로만 표시되며 탭 동작이 없다.
}

export function CommunitySummary({
  community,
  viewer,
  onJoin,
  onEnterChat,
  isJoinPending,
}: CommunitySummaryProps) {
  const metaLine = `${SPORT_CATEGORY_LABEL[community.sportCategory]} · ${
    VISIBILITY_LABEL[community.visibility]
  } · 멤버 ${community.memberCount}명`;
  const canEnterChat = viewer.kind === 'member' || viewer.kind === 'host';

  return (
    <Card>
      <ThemedText variant="primary" style={styles.name} accessibilityRole="header">
        {community.name}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.meta}>
        {metaLine}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.meta}>
        {`방장 #${community.hostUserId}`}
      </ThemedText>
      {community.description !== null && community.description.length > 0 ? (
        <ThemedText variant="secondary" style={styles.description}>
          {community.description}
        </ThemedText>
      ) : null}

      <View style={styles.ctaArea}>
        {viewer.kind === 'non-member' && (
          <Button label="가입하기" onPress={onJoin} loading={isJoinPending} />
        )}
        {viewer.kind === 'pending' && <Button label="승인 대기 중" onPress={noop} disabled />}
        {canEnterChat && (
          <Button label="채팅 입장" onPress={onEnterChat} disabled={community.roomId === null} />
        )}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  name: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 4,
  },
  meta: {
    fontSize: 13,
    marginTop: 2,
  },
  description: {
    fontSize: 14,
    marginTop: 12,
  },
  ctaArea: {
    marginTop: 16,
  },
});
