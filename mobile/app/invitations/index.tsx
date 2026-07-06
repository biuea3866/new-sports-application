/**
 * app/invitations/index.tsx — S7 초대 수신함(수락/거절) 화면.
 * 근거: design-fe-app.md S7, tickets/FE-13.
 *
 * 토스 패턴: 리스트 아이템당 명확한 2선택(수락/거절).
 * 4상태(loading/empty/error/success) 처리. 수락 성공 시 방목록·수신함 캐시 무효화는
 * useAcceptInvitation(FE-08) 내부 책임이며, 이 화면은 응답의 roomId로 방 이동만 수행한다.
 * 거절/수락 성공 시 로컬에서 즉시 카드를 제거해 목록을 최신 상태로 보여준다
 * (수신함 재조회 전에도 사용자가 결과를 즉시 확인할 수 있도록 함).
 *
 * InvitationResponse에는 방 이름·초대자 이름이 없어(roomId/inviterUserId만 보유),
 * 와이어프레임의 "주말 축구 모임 · 초대자: 김철수" 대신 식별자 기반 표기(`방 #{roomId}`,
 * `초대자 #{inviterUserId}`)로 대체한다 — 이름 해석 API가 계약에 없어 발생한 알려진 차이(open item).
 */
import { useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorView } from '../../components/ui/ErrorView';
import { LoadingView } from '../../components/ui/LoadingView';
import { ThemedText } from '../../components/ui/ThemedText';
import { useTheme } from '../../theme/useTheme';
import {
  useAcceptInvitation,
  useMyInvitations,
  useRejectInvitation,
} from '../../lib/useInvitations';
import { formatExpiryDDay, formatSpeakPermissionLabel } from '../../lib/invitationPresentation';
import { ROUTES } from '../../lib/navigation';
import type { InvitationResponse } from '../../api/chat-types';

const ACCEPT_ERROR_MESSAGE = '이미 만료되었거나 처리된 초대예요';
const REJECT_ERROR_MESSAGE = '요청을 처리하지 못했어요';

interface InvitationCardProps {
  invitation: InvitationResponse;
  onRemove: (id: number) => void;
}

function InvitationCard({ invitation, onRemove }: InvitationCardProps) {
  const router = useRouter();
  const acceptInvitation = useAcceptInvitation();
  const rejectInvitation = useRejectInvitation();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isActionPending = acceptInvitation.isPending || rejectInvitation.isPending;

  const handleAccept = () => {
    setErrorMessage(null);
    acceptInvitation.mutate(invitation.id, {
      onSuccess: (response: InvitationResponse) => {
        onRemove(invitation.id);
        router.push(ROUTES.rooms.detail(String(response.roomId)));
      },
      onError: () => {
        setErrorMessage(ACCEPT_ERROR_MESSAGE);
      },
    });
  };

  const handleReject = () => {
    setErrorMessage(null);
    rejectInvitation.mutate(invitation.id, {
      onSuccess: () => {
        onRemove(invitation.id);
      },
      onError: () => {
        setErrorMessage(REJECT_ERROR_MESSAGE);
      },
    });
  };

  return (
    <Card testID={`invitation-card-${invitation.id}`} style={styles.card}>
      <ThemedText variant="primary" style={styles.roomLabel}>
        {`방 #${invitation.roomId}`}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.metaLine}>
        {`${formatSpeakPermissionLabel(invitation.canSpeak)} · ${formatExpiryDDay(invitation.expiresAt)}`}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.inviterLine}>
        {`초대자 #${invitation.inviterUserId}`}
      </ThemedText>
      {errorMessage ? (
        <ThemedText
          variant="danger"
          style={styles.errorMessage}
          accessibilityRole="alert"
          accessibilityLabel={errorMessage}
        >
          {errorMessage}
        </ThemedText>
      ) : null}
      <View style={styles.actions}>
        <View style={styles.actionButton}>
          <Button
            label="거절"
            variant="surface"
            onPress={handleReject}
            disabled={isActionPending}
            loading={rejectInvitation.isPending}
          />
        </View>
        <View style={styles.actionButton}>
          <Button
            label="수락"
            onPress={handleAccept}
            disabled={isActionPending}
            loading={acceptInvitation.isPending}
          />
        </View>
      </View>
    </Card>
  );
}

export default function MyInvitationsScreen() {
  const { data, isLoading, isError, refetch } = useMyInvitations();
  const { tokens } = useTheme();
  const [removedIds, setRemovedIds] = useState<Set<number>>(new Set());

  const handleRemove = (id: number) => {
    setRemovedIds((previous) => {
      const next = new Set(previous);
      next.add(id);
      return next;
    });
  };

  if (isLoading) {
    return <LoadingView variant="skeleton" testID="invitations-loading" />;
  }

  if (isError) {
    return <ErrorView message="받은 초대를 불러오지 못했어요" onRetry={() => refetch()} />;
  }

  const invitations = (data ?? []).filter((invitation) => !removedIds.has(invitation.id));

  return (
    <View
      testID="invitations-screen-root"
      style={[styles.container, { backgroundColor: tokens.background }]}
    >
      <ThemedText variant="primary" style={styles.header} accessibilityRole="header">
        받은 초대
      </ThemedText>
      {invitations.length === 0 ? (
        <EmptyState message="받은 초대가 없어요" />
      ) : (
        <FlatList
          data={invitations}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <InvitationCard invitation={item} onRemove={handleRemove} />}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 56,
  },
  header: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 16,
  },
  list: {
    paddingBottom: 40,
  },
  card: {
    marginBottom: 12,
  },
  roomLabel: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 4,
  },
  metaLine: {
    fontSize: 13,
    marginBottom: 2,
  },
  inviterLine: {
    fontSize: 13,
    marginBottom: 12,
  },
  errorMessage: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 12,
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
  },
  actionButton: {
    flex: 1,
  },
});
