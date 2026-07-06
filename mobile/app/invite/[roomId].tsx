/**
 * app/invite/[roomId].tsx — S6 게스트 초대 발송 화면.
 * 근거: design-fe-app.md S6, tickets/FE-13.
 *
 * 토스 패턴: 단일 과업(초대) + 하단 단일 CTA.
 * 방장만 진입 가능(FR-11)하다는 전제는 FE-10/FE-15가 진입 버튼 노출로 제어하며,
 * 이 화면은 폼 렌더링·제출 배선만 책임진다.
 *
 * 멱등 처리: BE는 동일 (roomId, inviteeUserId) PENDING 초대가 이미 있으면 신규 생성 대신
 * 기존 초대를 그대로 응답한다. `isReusedPendingInvitation`으로 재사용 여부를 판단해
 * 재사용이면 인라인 안내만 표시하고, 신규 생성이면 방으로 복귀한다.
 */
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { Button } from '../../components/ui/Button';
import { SegmentedControl } from '../../components/ui/SegmentedControl';
import { ThemedText } from '../../components/ui/ThemedText';
import { useTheme } from '../../theme/useTheme';
import { useInviteGuest } from '../../lib/useInvitations';
import { isReusedPendingInvitation } from '../../lib/invitationPresentation';
import type { InvitationResponse } from '../../api/chat-types';

const SPEAK_PERMISSION_OPTIONS = [
  { label: '발화 가능', value: 'true' },
  { label: '읽기 전용', value: 'false' },
];

const DEFAULT_EXPIRES_IN_DAYS = '7';
const REUSED_INVITATION_MESSAGE = '이미 대기 중인 초대가 있어요';
const SUBMIT_FAILED_MESSAGE = '초대를 보내지 못했어요. 다시 시도해주세요.';

function parsePositiveInteger(value: string): number | null {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return null;
  }
  return parsed;
}

export default function InviteGuestScreen() {
  const { roomId } = useLocalSearchParams<{ roomId: string }>();
  const router = useRouter();
  const { tokens } = useTheme();
  const { mutate, isPending } = useInviteGuest();

  const [inviteeUserId, setInviteeUserId] = useState('');
  const [canSpeak, setCanSpeak] = useState('true');
  const [expiresInDays, setExpiresInDays] = useState(DEFAULT_EXPIRES_IN_DAYS);
  const [inlineMessage, setInlineMessage] = useState<string | null>(null);

  const parsedRoomId = Number(roomId);
  const parsedInviteeUserId = parsePositiveInteger(inviteeUserId);
  const parsedExpiresInDays = parsePositiveInteger(expiresInDays);
  const isSubmitDisabled =
    isPending || parsedInviteeUserId === null || parsedExpiresInDays === null;

  const handleSubmit = () => {
    if (parsedInviteeUserId === null || parsedExpiresInDays === null) {
      return;
    }
    setInlineMessage(null);
    const submittedAt = new Date();

    mutate(
      {
        roomId: parsedRoomId,
        request: {
          inviteeUserId: parsedInviteeUserId,
          canSpeak: canSpeak === 'true',
          expiresInDays: parsedExpiresInDays,
        },
      },
      {
        onSuccess: (response: InvitationResponse) => {
          if (isReusedPendingInvitation(response.createdAt, submittedAt)) {
            setInlineMessage(REUSED_INVITATION_MESSAGE);
            return;
          }
          router.back();
        },
        onError: () => {
          setInlineMessage(SUBMIT_FAILED_MESSAGE);
        },
      }
    );
  };

  return (
    <KeyboardAvoidingView
      style={[styles.container, { backgroundColor: tokens.background }]}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ThemedText variant="primary" style={styles.header} accessibilityRole="header">
        게스트 초대
      </ThemedText>

      <View style={styles.field}>
        <ThemedText variant="secondary" style={styles.label}>
          대상 사용자
        </ThemedText>
        <TextInput
          style={[
            styles.input,
            {
              borderColor: tokens.border,
              color: tokens.textPrimary,
              backgroundColor: tokens.surfaceElevated,
            },
          ]}
          value={inviteeUserId}
          onChangeText={setInviteeUserId}
          placeholder="사용자 ID"
          placeholderTextColor={tokens.textTertiary}
          keyboardType="number-pad"
          accessibilityLabel="대상 사용자 ID 입력"
        />
      </View>

      <View style={styles.field}>
        <ThemedText variant="secondary" style={styles.label}>
          발화 권한
        </ThemedText>
        <SegmentedControl
          options={SPEAK_PERMISSION_OPTIONS}
          value={canSpeak}
          onChange={setCanSpeak}
        />
      </View>

      <View style={styles.field}>
        <ThemedText variant="secondary" style={styles.label}>
          참여 기간(일)
        </ThemedText>
        <TextInput
          style={[
            styles.input,
            {
              borderColor: tokens.border,
              color: tokens.textPrimary,
              backgroundColor: tokens.surfaceElevated,
            },
          ]}
          value={expiresInDays}
          onChangeText={setExpiresInDays}
          keyboardType="number-pad"
          accessibilityLabel="참여 기간 일수 입력"
        />
      </View>

      {inlineMessage ? (
        <ThemedText
          variant="danger"
          style={styles.inlineMessage}
          accessibilityRole="alert"
          accessibilityLabel={inlineMessage}
        >
          {inlineMessage}
        </ThemedText>
      ) : null}

      <View style={styles.ctaArea}>
        <Button
          label="초대 보내기"
          onPress={handleSubmit}
          disabled={isSubmitDisabled}
          loading={isPending}
        />
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 56,
  },
  header: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 24,
  },
  field: {
    marginBottom: 20,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
  },
  inlineMessage: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 12,
  },
  ctaArea: {
    marginTop: 'auto',
    paddingBottom: 24,
  },
});
