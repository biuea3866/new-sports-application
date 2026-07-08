/**
 * CommunityMemberList — 역할 배지를 포함한 ACTIVE 멤버 리스트.
 * 방장에게만 멤버별 강퇴·위임 액션을 노출한다(FR-3). 노출 여부는 `canManage`(= canManageMembers
 * 판정 결과)를 그대로 받아 렌더링만 담당한다.
 */
import { Pressable, View, StyleSheet } from 'react-native';

import type { CommunityMemberResponse } from '../../api/community-types';
import { useTheme } from '../../theme/useTheme';
import { EmptyState, ListItem, ThemedText } from '../ui';

const ROLE_LABEL: Record<CommunityMemberResponse['role'], string> = {
  HOST: '방장',
  MEMBER: '멤버',
};

export interface CommunityMemberListProps {
  members: CommunityMemberResponse[];
  canManage: boolean;
  onKick: (member: CommunityMemberResponse) => void;
  onTransfer: (member: CommunityMemberResponse) => void;
}

interface RoleBadgeProps {
  role: CommunityMemberResponse['role'];
}

function RoleBadge({ role }: RoleBadgeProps) {
  const { tokens } = useTheme();

  return (
    <View
      style={[
        styles.roleBadge,
        { backgroundColor: tokens.surfaceElevated, borderColor: tokens.border },
      ]}
      accessible
      accessibilityRole="text"
      accessibilityLabel={ROLE_LABEL[role]}
    >
      <ThemedText variant={role === 'HOST' ? 'accent' : 'secondary'} style={styles.roleBadgeText}>
        {ROLE_LABEL[role]}
      </ThemedText>
    </View>
  );
}

interface MemberActionsProps {
  member: CommunityMemberResponse;
  onKick: (member: CommunityMemberResponse) => void;
  onTransfer: (member: CommunityMemberResponse) => void;
}

function MemberActions({ member, onKick, onTransfer }: MemberActionsProps) {
  return (
    <View style={styles.actions}>
      <Pressable
        style={styles.actionButton}
        onPress={() => onKick(member)}
        accessibilityRole="button"
        accessibilityLabel={`사용자 #${member.userId} 강퇴`}
      >
        <ThemedText variant="danger" style={styles.actionText}>
          강퇴
        </ThemedText>
      </Pressable>
      <Pressable
        style={styles.actionButton}
        onPress={() => onTransfer(member)}
        accessibilityRole="button"
        accessibilityLabel={`사용자 #${member.userId} 방장 위임`}
      >
        <ThemedText variant="accent" style={styles.actionText}>
          위임
        </ThemedText>
      </Pressable>
    </View>
  );
}

export function CommunityMemberList({
  members,
  canManage,
  onKick,
  onTransfer,
}: CommunityMemberListProps) {
  return (
    <View>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        {`멤버 (${members.length})`}
      </ThemedText>

      {members.length === 0 ? (
        <EmptyState message="멤버가 없어요" />
      ) : (
        members.map((memberItem) => {
          const showActions = canManage && memberItem.role !== 'HOST';

          return (
            <ListItem
              key={memberItem.id}
              title={`사용자 #${memberItem.userId}`}
              leading={<RoleBadge role={memberItem.role} />}
              trailing={
                showActions ? (
                  <MemberActions member={memberItem} onKick={onKick} onTransfer={onTransfer} />
                ) : undefined
              }
            />
          );
        })
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  title: {
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 8,
  },
  roleBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    borderWidth: 1,
  },
  roleBadgeText: {
    fontSize: 12,
    fontWeight: '600',
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
  },
  actionButton: {
    paddingHorizontal: 4,
    paddingVertical: 4,
  },
  actionText: {
    fontSize: 13,
    fontWeight: '600',
  },
});
