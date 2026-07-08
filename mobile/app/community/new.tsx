/**
 * 게시글 작성 화면 — A-P3 (전역 게시글 작성 + 모임 게시글 작성 공용)
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "화면 목록" A-P3,
 * "API 연동 표"(모임글은 BE가 소속 모임 종목을 상속하므로 sportCategory 미전송),
 * "화면별 4상태 표" A-P3(403→"작성 권한이 없어요"(NOTICE 비host 포함)).
 *
 * `?communityId=` 쿼리 파라미터 유무로 분기한다: 모임글이면 종목 선택 UI를 숨기고
 * `communityId`만 전송, 전역글이면 종목 선택 칩(SportCategoryChips)을 노출한다.
 */
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import type { SportCategory } from '../../api/community-types';
import { SportCategoryChips } from '../../components/community/SportCategoryChips';
import { ThemedText, ThemedView } from '../../components/ui';
import { isForbiddenError } from '../../lib/http-error';
import { useCreatePost } from '../../lib/usePosts';
import { useTheme } from '../../theme/useTheme';

const FORBIDDEN_MESSAGE = '작성 권한이 없어요';
const GENERIC_ERROR_MESSAGE = '게시글 등록에 실패했습니다. 다시 시도해주세요.';

export default function CommunityNewScreen() {
  const { tokens } = useTheme();
  const router = useRouter();
  const { communityId: communityIdParam } = useLocalSearchParams<{ communityId?: string }>();
  const communityId =
    typeof communityIdParam === 'string' && communityIdParam.length > 0
      ? Number(communityIdParam)
      : null;
  const isCommunityPost = communityId !== null && !Number.isNaN(communityId);

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [sportCategory, setSportCategory] = useState<SportCategory | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { mutate, isPending } = useCreatePost();

  const isSubmitDisabled = title.trim().length === 0 || content.trim().length === 0 || isPending;

  const handleSubmit = () => {
    if (isSubmitDisabled) return;
    setErrorMessage(null);
    mutate(
      {
        title: title.trim(),
        content: content.trim(),
        communityId: isCommunityPost ? communityId : undefined,
        sportCategory: isCommunityPost ? undefined : sportCategory,
      },
      {
        onSuccess: () => {
          router.back();
        },
        onError: (error: unknown) => {
          setErrorMessage(
            isForbiddenError(error as Error) ? FORBIDDEN_MESSAGE : GENERIC_ERROR_MESSAGE
          );
        },
      }
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ThemedView style={styles.header} background="background">
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
        >
          <ThemedText variant="secondary" style={styles.cancelText}>
            취소
          </ThemedText>
        </Pressable>
        <ThemedText variant="primary" style={styles.headerTitle} accessibilityRole="header">
          게시글 작성
        </ThemedText>
        <Pressable
          onPress={handleSubmit}
          disabled={isSubmitDisabled}
          accessibilityRole="button"
          accessibilityLabel="게시글 등록"
          accessibilityState={{ disabled: isSubmitDisabled, busy: isPending }}
        >
          <ThemedText variant={isSubmitDisabled ? 'muted' : 'accent'} style={styles.submitText}>
            {isPending ? '등록 중' : '등록'}
          </ThemedText>
        </Pressable>
      </ThemedView>

      <ScrollView style={styles.body} keyboardShouldPersistTaps="handled">
        {errorMessage !== null && (
          <ThemedText
            variant="danger"
            style={styles.errorText}
            accessibilityRole="alert"
            accessibilityLabel={errorMessage}
          >
            {errorMessage}
          </ThemedText>
        )}

        {!isCommunityPost && (
          <View style={styles.fieldGroup}>
            <ThemedText variant="secondary" style={styles.label}>
              종목(선택)
            </ThemedText>
            <SportCategoryChips selected={sportCategory} onSelect={setSportCategory} />
          </View>
        )}

        <View style={styles.fieldGroup}>
          <ThemedText nativeID="titleLabel" variant="secondary" style={styles.label}>
            제목 *
          </ThemedText>
          <TextInput
            style={[styles.titleInput, { borderColor: tokens.border, color: tokens.textPrimary }]}
            value={title}
            onChangeText={setTitle}
            placeholder="제목을 입력하세요"
            placeholderTextColor={tokens.textTertiary}
            maxLength={200}
            accessibilityLabel="제목 입력"
            returnKeyType="next"
          />
        </View>

        <View style={styles.fieldGroup}>
          <ThemedText nativeID="contentLabel" variant="secondary" style={styles.label}>
            내용 *
          </ThemedText>
          <TextInput
            style={[styles.contentInput, { borderColor: tokens.border, color: tokens.textPrimary }]}
            value={content}
            onChangeText={setContent}
            placeholder="내용을 입력하세요"
            placeholderTextColor={tokens.textTertiary}
            maxLength={10000}
            multiline
            textAlignVertical="top"
            accessibilityLabel="내용 입력"
          />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
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
  headerTitle: {
    fontSize: 17,
    fontWeight: '600',
  },
  cancelText: {
    fontSize: 17,
  },
  submitText: {
    fontSize: 17,
    fontWeight: '600',
  },
  body: {
    flex: 1,
    padding: 20,
  },
  errorText: {
    fontSize: 14,
    marginBottom: 16,
  },
  fieldGroup: {
    marginBottom: 24,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
  },
  titleInput: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
  },
  contentInput: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    minHeight: 200,
  },
});
