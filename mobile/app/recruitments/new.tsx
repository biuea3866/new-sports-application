/**
 * 모집 개설 폼 — A-R3
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-R3(토스 송금·상품등록 패턴 — 한 화면 한
 * 과업, 하단 고정 단일 CTA), Testing Plan "개설 폼".
 *
 * 날짜/시각은 date-picker 라이브러리가 레포에 없어 ISO 형태 텍스트 입력
 * (`YYYY-MM-DDTHH:mm`)으로 받는다(`lib/recruitment-form.ts` 참조).
 */
import { useState } from 'react';
import { ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import { useCreateRecruitment } from '../../lib/useRecruitment';
import {
  isCreateRecruitmentFormValid,
  toCreateRecruitmentRequest,
  validateCreateRecruitmentForm,
  type CreateRecruitmentFormValues,
} from '../../lib/recruitment-form';
import { Button, ThemedText } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';

const SUBMIT_ERROR_MESSAGE = '개설에 실패했습니다. 다시 시도해주세요.';

export default function RecruitmentNewScreen() {
  const { tokens } = useTheme();
  const params = useLocalSearchParams<{ communityId?: string }>();
  const communityId = typeof params.communityId === 'string' ? Number(params.communityId) : null;

  const { mutate: createRecruitment, isPending } = useCreateRecruitment();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [capacityText, setCapacityText] = useState('');
  const [feeAmountText, setFeeAmountText] = useState('');
  const [activityAt, setActivityAt] = useState('');
  const [applicationDeadline, setApplicationDeadline] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);

  const formValues: CreateRecruitmentFormValues = {
    title,
    description,
    capacityText,
    feeAmountText,
    activityAt,
    applicationDeadline,
    communityId,
  };
  const errors = validateCreateRecruitmentForm(formValues);
  const isFormValid = isCreateRecruitmentFormValid(formValues);

  function handleSubmit() {
    if (!isFormValid) {
      return;
    }

    setSubmitError(null);
    createRecruitment(toCreateRecruitmentRequest(formValues), {
      onSuccess: (result) => {
        router.replace(`/recruitments/${result.id}`);
      },
      onError: () => {
        setSubmitError(SUBMIT_ERROR_MESSAGE);
      },
    });
  }

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        모집 개설
      </ThemedText>

      <ScrollView style={styles.form} contentContainerStyle={styles.formContent}>
        <ThemedText variant="secondary" style={styles.label}>
          제목
        </ThemedText>
        <TextInput
          value={title}
          onChangeText={setTitle}
          placeholder="예: 주말 축구 3명 모집"
          placeholderTextColor={tokens.textTertiary}
          editable={!isPending}
          style={[
            styles.input,
            {
              backgroundColor: tokens.surface,
              color: tokens.textPrimary,
              borderColor: tokens.border,
            },
          ]}
          accessibilityLabel="제목"
        />

        <ThemedText variant="secondary" style={styles.label}>
          설명
        </ThemedText>
        <TextInput
          value={description}
          onChangeText={setDescription}
          placeholder="모집 설명 (선택)"
          placeholderTextColor={tokens.textTertiary}
          editable={!isPending}
          style={[
            styles.input,
            {
              backgroundColor: tokens.surface,
              color: tokens.textPrimary,
              borderColor: tokens.border,
            },
          ]}
          accessibilityLabel="설명"
        />

        <ThemedText variant="secondary" style={styles.label}>
          정원
        </ThemedText>
        <TextInput
          value={capacityText}
          onChangeText={setCapacityText}
          placeholder="예: 3"
          placeholderTextColor={tokens.textTertiary}
          keyboardType="number-pad"
          editable={!isPending}
          style={[
            styles.input,
            {
              backgroundColor: tokens.surface,
              color: tokens.textPrimary,
              borderColor: tokens.border,
            },
          ]}
          accessibilityLabel="정원"
        />
        {errors.capacity ? (
          <ThemedText variant="danger" style={styles.fieldError}>
            {errors.capacity}
          </ThemedText>
        ) : null}

        <ThemedText variant="secondary" style={styles.label}>
          참가비
        </ThemedText>
        <TextInput
          value={feeAmountText}
          onChangeText={setFeeAmountText}
          placeholder="예: 5000 (무료면 0)"
          placeholderTextColor={tokens.textTertiary}
          keyboardType="number-pad"
          editable={!isPending}
          style={[
            styles.input,
            {
              backgroundColor: tokens.surface,
              color: tokens.textPrimary,
              borderColor: tokens.border,
            },
          ]}
          accessibilityLabel="참가비"
        />
        {errors.feeAmount ? (
          <ThemedText variant="danger" style={styles.fieldError}>
            {errors.feeAmount}
          </ThemedText>
        ) : null}

        <ThemedText variant="secondary" style={styles.label}>
          활동일시
        </ThemedText>
        <TextInput
          value={activityAt}
          onChangeText={setActivityAt}
          placeholder="YYYY-MM-DDTHH:mm"
          placeholderTextColor={tokens.textTertiary}
          editable={!isPending}
          style={[
            styles.input,
            {
              backgroundColor: tokens.surface,
              color: tokens.textPrimary,
              borderColor: tokens.border,
            },
          ]}
          accessibilityLabel="활동일시"
        />

        <ThemedText variant="secondary" style={styles.label}>
          신청마감
        </ThemedText>
        <TextInput
          value={applicationDeadline}
          onChangeText={setApplicationDeadline}
          placeholder="YYYY-MM-DDTHH:mm"
          placeholderTextColor={tokens.textTertiary}
          editable={!isPending}
          style={[
            styles.input,
            {
              backgroundColor: tokens.surface,
              color: tokens.textPrimary,
              borderColor: tokens.border,
            },
          ]}
          accessibilityLabel="신청마감"
        />
        {errors.deadline ? (
          <ThemedText variant="danger" style={styles.fieldError}>
            {errors.deadline}
          </ThemedText>
        ) : null}

        {submitError ? (
          <ThemedText
            variant="danger"
            style={styles.submitError}
            accessibilityRole="alert"
            accessibilityLabel={submitError}
          >
            {submitError}
          </ThemedText>
        ) : null}
      </ScrollView>

      <View style={styles.ctaWrapper}>
        <Button
          label="개설하기"
          onPress={handleSubmit}
          disabled={!isFormValid}
          loading={isPending}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 16,
    paddingHorizontal: 16,
  },
  form: {
    flex: 1,
  },
  formContent: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 6,
    marginTop: 16,
  },
  input: {
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 15,
  },
  fieldError: {
    fontSize: 12,
    marginTop: 4,
  },
  submitError: {
    fontSize: 13,
    marginTop: 16,
  },
  ctaWrapper: {
    paddingHorizontal: 16,
    paddingBottom: 24,
    paddingTop: 12,
  },
});
