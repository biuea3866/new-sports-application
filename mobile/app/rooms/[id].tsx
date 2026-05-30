/**
 * 채팅방 화면 — 메시지 목록 + 입력창
 */
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { useMessages, useSendMessage } from '../../lib/useRooms';
import type { MessageResponse } from '../../api/types';

interface MessageItemProps {
  message: MessageResponse;
}

function MessageItem({ message }: MessageItemProps) {
  const formattedTime = new Date(message.sentAt).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <View style={styles.messageCard} accessibilityLabel={`메시지: ${message.content}`}>
      <Text style={styles.messageSender} accessibilityRole="text">
        사용자 {message.senderId}
      </Text>
      <Text style={styles.messageContent} accessibilityRole="text">
        {message.content}
      </Text>
      <Text style={styles.messageTime} accessibilityRole="text">
        {formattedTime}
      </Text>
    </View>
  );
}

export default function RoomDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const roomId = Number(id);
  const [inputText, setInputText] = useState('');

  const { data, isLoading, isError } = useMessages(roomId);
  const { mutate: sendMessage, isPending } = useSendMessage();

  const handleSend = () => {
    const trimmed = inputText.trim();
    if (!trimmed || isPending) return;

    sendMessage(
      { roomId, content: trimmed },
      {
        onSuccess: () => setInputText(''),
      }
    );
  };

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="메시지 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.centered} accessibilityLabel="메시지 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          메시지를 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  const messages = data?.messages ?? [];

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      <View style={styles.messageList} accessible={true} accessibilityLabel="메시지 목록">
        {messages.length === 0 ? (
          <Text style={styles.emptyText} accessibilityRole="text">
            메시지가 없습니다. 첫 메시지를 보내보세요.
          </Text>
        ) : (
          <FlatList
            data={messages}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => <MessageItem message={item} />}
            inverted
            contentContainerStyle={styles.list}
          />
        )}
      </View>

      <View style={styles.inputArea}>
        <TextInput
          style={styles.textInput}
          value={inputText}
          onChangeText={setInputText}
          placeholder="메시지를 입력하세요"
          placeholderTextColor="#8E8E93"
          multiline
          maxLength={1000}
          accessibilityLabel="메시지 입력"
          accessibilityHint="메시지를 입력하고 전송 버튼을 누르세요"
          editable={!isPending}
        />
        <Pressable
          style={[styles.sendButton, (!inputText.trim() || isPending) && styles.sendButtonDisabled]}
          onPress={handleSend}
          disabled={!inputText.trim() || isPending}
          accessibilityRole="button"
          accessibilityLabel="메시지 전송"
          accessibilityState={{ disabled: !inputText.trim() || isPending }}
        >
          <Text style={styles.sendButtonText}>{isPending ? '...' : '전송'}</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
  messageList: {
    flex: 1,
    paddingHorizontal: 16,
  },
  list: {
    paddingTop: 12,
    paddingBottom: 8,
  },
  messageCard: {
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 12,
    marginBottom: 8,
    maxWidth: '85%',
  },
  messageSender: {
    fontSize: 12,
    fontWeight: '600',
    color: '#3C3C43',
    marginBottom: 4,
  },
  messageContent: {
    fontSize: 15,
    color: '#1C1C1E',
    lineHeight: 20,
  },
  messageTime: {
    fontSize: 11,
    color: '#8E8E93',
    marginTop: 4,
    textAlign: 'right',
  },
  emptyText: {
    fontSize: 15,
    color: '#8E8E93',
    textAlign: 'center',
    marginTop: 40,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
  inputArea: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
    backgroundColor: '#fff',
  },
  textInput: {
    flex: 1,
    backgroundColor: '#F2F2F7',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 15,
    color: '#1C1C1E',
    maxHeight: 100,
    marginRight: 8,
  },
  sendButton: {
    backgroundColor: '#007AFF',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  sendButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
  },
});
