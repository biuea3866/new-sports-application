/**
 * 커뮤니티(동아리) 개설 화면 — S4
 *
 * 근거: FE-11 티켓, `20260704-채팅시스템고도화-design-fe-app.md` S4.
 * 토스 패턴: 한 화면 한 과업 폼, 하단 고정 단일 CTA, 유효성 인라인.
 *
 * `useCreateCommunity`(FE-07)로 제출한다. 성공 시 상세(S5)로 이동한다 —
 * 방목록(`rooms/me`) 캐시 무효화는 `useCreateCommunity`가 처리한다(전용 그룹 방 자동 생성 반영).
 */
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { router } from 'expo-router';

import { Button, SegmentedControl, ThemedText } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';
import { useCreateCommunity } from '../../lib/useCommunity';
import { SPORT_CATEGORY_OPTIONS } from '../../lib/community-format';
import {
  isCreateCommunityFormValid,
  toCreateCommunityRequest,
  type CreateCommunityFormValues,
} from '../../lib/community-form';
import type { CommunityVisibility, SportCategory } from '../../api/community-types';

const VISIBILITY_OPTIONS = [
  { label: '공개', value: 'PUBLIC' },
  { label: '비공개', value: 'PRIVATE' },
];

const SUBMIT_ERROR_MESSAGE = '개설에 실패했습니다. 다시 시도해주세요.';

function isCommunityVisibility(value: string): value is CommunityVisibility {
  return value === 'PUBLIC' || value === 'PRIVATE';
}

export default function CommunityNewScreen() {
  const { tokens } = useTheme();
  const { mutate: createCommunity, isPending } = useCreateCommunity();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [sportCategory, setSportCategory] = useState<SportCategory | null>(null);
  const [visibility, setVisibility] = useState<CommunityVisibility>('PUBLIC');
  const [submitError, setSubmitError] = useState<string | null>(null);

  const formValues: CreateCommunityFormValues = {
    name,
    description,
    sportCategory,
    visibility,
  };
  const isFormValid = isCreateCommunityFormValid(formValues);

  const handleVisibilityChange = (value: string) => {
    if (isCommunityVisibility(value)) {
      setVisibility(value);
    }
  };

  const handleSubmit = () => {
    if (!isFormValid) {
      return;
    }

    setSubmitError(null);
    createCommunity(toCreateCommunityRequest(formValues), {
      onSuccess: (result) => {
        router.replace(`/communities/${result.id}`);
      },
      onError: () => {
        setSubmitError(SUBMIT_ERROR_MESSAGE);
      },
    });
  };

  return (
    <View style={[styles.container, { backgroundColor: tokens.background }]}>
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        동아리 개설
      </ThemedText>

      <ScrollView style={styles.form} contentContainerStyle={styles.formContent}>
        <ThemedText variant="secondary" style={styles.label}>
          이름
        </ThemedText>
        <TextInput
          value={name}
          onChangeText={setName}
          placeholder="동아리 이름"
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
          accessibilityLabel="이름"
        />

        <ThemedText variant="secondary" style={styles.label}>
          설명
        </ThemedText>
        <TextInput
          value={description}
          onChangeText={setDescription}
          placeholder="동아리 설명 (선택)"
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
          종목
        </ThemedText>
        <View style={styles.sportCategoryRow} accessibilityLabel="종목 목록">
          {SPORT_CATEGORY_OPTIONS.map((option) => {
            const isSelected = option.value === sportCategory;

            return (
              <Pressable
                key={option.value}
                onPress={() => setSportCategory(option.value)}
                disabled={isPending}
                style={[
                  styles.sportCategoryChip,
                  { borderColor: tokens.border, backgroundColor: tokens.surface },
                  isSelected && {
                    borderColor: tokens.accent,
                    backgroundColor: tokens.surfaceElevated,
                  },
                ]}
                accessibilityRole="radio"
                accessibilityLabel={option.label}
                accessibilityState={{ selected: isSelected }}
              >
                <ThemedText
                  variant={isSelected ? 'accent' : 'secondary'}
                  style={styles.sportCategoryLabel}
                >
                  {option.label}
                </ThemedText>
              </Pressable>
            );
          })}
        </View>

        <ThemedText variant="secondary" style={styles.label}>
          공개
        </ThemedText>
        <SegmentedControl
          options={VISIBILITY_OPTIONS}
          value={visibility}
          onChange={handleVisibilityChange}
        />

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
  sportCategoryRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  sportCategoryChip: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    borderWidth: 1.5,
  },
  sportCategoryLabel: {
    fontSize: 13,
    fontWeight: '600',
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
