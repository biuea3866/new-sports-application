/**
 * MessageComposer — 하단 단일 입력 CTA. 발화 권한(`canSpeak`)이 없는 읽기 전용 게스트는
 * 입력창이 비활성되고 안내 문구가 표시된다.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` S2 와이어프레임
 * "[메시지 입력…] [전송] — 단일 입력 + 전송(발화권한 없으면 비활성+안내)",
 * 티켓 `FE-10-room-chat-realtime-screen.md` 테스트 케이스
 * "읽기 전용 게스트는 입력창이 비활성이고 안내가 표시된다".
 *
 * 순수 프레젠테이션 — 전송/타이핑 신호 발행은 컨테이너가 주입한 콜백(`onSend`/`onTypingChange`)에
 * 위임하고, 이 컴포넌트는 입력 텍스트 자체(뷰 지역 상태)만 관리한다.
 */
import { useState } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';

import { ThemedText } from '../themed/ThemedText';
import { useTheme } from '../../theme/useTheme';

export interface MessageComposerProps {
  /** 발화 권한. false면 읽기 전용 게스트 — 입력창 비활성 + 안내. */
  canSpeak: boolean;
  /** 전송 진행 중이면 true — 중복 전송 방지. */
  sending?: boolean;
  onSend: (content: string) => void;
  /** 입력 텍스트 유무 변화(타이핑 시작/종료)를 컨테이너에 알린다. */
  onTypingChange: (typing: boolean) => void;
}

const READ_ONLY_GUIDE_TEXT = '읽기 전용으로 초대되었어요';

export function MessageComposer({
  canSpeak,
  sending = false,
  onSend,
  onTypingChange,
}: MessageComposerProps) {
  const { tokens } = useTheme();
  const [text, setText] = useState('');

  const isInputDisabled = !canSpeak || sending;
  const trimmedText = text.trim();
  const isSendDisabled = isInputDisabled || trimmedText.length === 0;

  const handleChangeText = (value: string) => {
    setText(value);
    onTypingChange(value.trim().length > 0);
  };

  const handleSend = () => {
    if (isSendDisabled) {
      return;
    }
    onSend(trimmedText);
    onTypingChange(false);
    setText('');
  };

  return (
    <View style={[styles.container, { borderTopColor: tokens.border }]}>
      <TextInput
        style={[styles.input, { backgroundColor: tokens.surface, color: tokens.textPrimary }]}
        value={text}
        onChangeText={handleChangeText}
        editable={!isInputDisabled}
        placeholder="메시지 입력…"
        placeholderTextColor={tokens.textTertiary}
        multiline
        maxLength={1000}
        accessibilityLabel="메시지 입력"
        accessibilityState={{ disabled: isInputDisabled }}
      />
      <TouchableOpacity
        style={[
          styles.sendButton,
          { backgroundColor: tokens.accent },
          isSendDisabled && styles.sendButtonDisabled,
        ]}
        onPress={handleSend}
        disabled={isSendDisabled}
        accessibilityRole="button"
        accessibilityLabel="메시지 전송"
        accessibilityState={{ disabled: isSendDisabled }}
      >
        <ThemedText variant="onAccent" style={styles.sendButtonLabel}>
          전송
        </ThemedText>
      </TouchableOpacity>
      {!canSpeak ? (
        <ThemedText variant="muted" style={styles.guideText} accessibilityRole="text">
          {READ_ONLY_GUIDE_TEXT}
        </ThemedText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'flex-end',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: 1,
  },
  input: {
    flex: 1,
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 15,
    maxHeight: 100,
    marginRight: 8,
  },
  sendButton: {
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonDisabled: {
    opacity: 0.5,
  },
  sendButtonLabel: {
    fontSize: 15,
    fontWeight: '600',
  },
  guideText: {
    width: '100%',
    fontSize: 12,
    marginTop: 6,
  },
});
