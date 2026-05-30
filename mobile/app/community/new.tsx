/**
 * 커뮤니티 게시글 작성 화면
 */
import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useCreatePost } from '../../lib/usePosts';

export default function CommunityNewScreen() {
  const router = useRouter();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const { mutate, isPending, isError } = useCreatePost();

  const isSubmitDisabled = title.trim().length === 0 || content.trim().length === 0 || isPending;

  const handleSubmit = () => {
    if (isSubmitDisabled) return;
    mutate(
      { title: title.trim(), content: content.trim() },
      {
        onSuccess: () => {
          router.back();
        },
      }
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.header}>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
        >
          <Text style={styles.cancelText}>취소</Text>
        </Pressable>
        <Text style={styles.headerTitle} accessibilityRole="header">
          게시글 작성
        </Text>
        <Pressable
          onPress={handleSubmit}
          disabled={isSubmitDisabled}
          accessibilityRole="button"
          accessibilityLabel="게시글 등록"
        >
          {isPending ? (
            <ActivityIndicator size="small" color="#007AFF" />
          ) : (
            <Text style={[styles.submitText, isSubmitDisabled && styles.submitTextDisabled]}>
              등록
            </Text>
          )}
        </Pressable>
      </View>

      <ScrollView style={styles.body} keyboardShouldPersistTaps="handled">
        {isError && (
          <Text style={styles.errorText} accessibilityRole="alert">
            게시글 등록에 실패했습니다. 다시 시도해주세요.
          </Text>
        )}

        <View style={styles.fieldGroup}>
          <Text nativeID="titleLabel" style={styles.label}>
            제목 *
          </Text>
          <TextInput
            style={styles.titleInput}
            value={title}
            onChangeText={setTitle}
            placeholder="제목을 입력하세요"
            placeholderTextColor="#C7C7CC"
            maxLength={200}
            accessibilityLabel="제목 입력"
            accessibilityLabelledBy="titleLabel"
            returnKeyType="next"
          />
        </View>

        <View style={styles.fieldGroup}>
          <Text nativeID="contentLabel" style={styles.label}>
            내용 *
          </Text>
          <TextInput
            style={styles.contentInput}
            value={content}
            onChangeText={setContent}
            placeholder="내용을 입력하세요"
            placeholderTextColor="#C7C7CC"
            maxLength={10000}
            multiline
            textAlignVertical="top"
            accessibilityLabel="내용 입력"
            accessibilityLabelledBy="contentLabel"
          />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 56,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  cancelText: {
    fontSize: 17,
    color: '#8E8E93',
  },
  submitText: {
    fontSize: 17,
    fontWeight: '600',
    color: '#007AFF',
  },
  submitTextDisabled: {
    color: '#C7C7CC',
  },
  body: {
    flex: 1,
    padding: 20,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 14,
    marginBottom: 16,
  },
  fieldGroup: {
    marginBottom: 24,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#3C3C43',
    marginBottom: 8,
  },
  titleInput: {
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: '#1C1C1E',
  },
  contentInput: {
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: '#1C1C1E',
    minHeight: 200,
  },
});
