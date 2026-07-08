/**
 * 소모임 예약 연결 화면 — A-B2 (방장 전용)
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * A-B2(토스 슬롯 선택 패턴, `useSlots` 재사용) · "화면별 4상태 표" A-B2.
 *
 * 시설 검색 API(키워드 검색)가 아직 없어(`api/facility.ts`는 `gu`/`type` 필터만 지원),
 * 시설 ID를 직접 입력해 슬롯을 조회하는 방식으로 단순화한다(design-fe-app "Open Questions"의
 * facility 선택 UI는 후속 과제 — 시설 목록·상세에서 진입 시 `facilityId` 쿼리 파라미터로
 * 선진입도 지원한다).
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import type { SlotResponse } from '../../../../api/types';
import {
  Button,
  EmptyState,
  ErrorView,
  LoadingView,
  ThemedText,
  ThemedView,
} from '../../../../components/ui';
import { isForbiddenError } from '../../../../lib/http-error';
import { useSlots } from '../../../../lib/useBooking';
import { useLinkCommunityBooking } from '../../../../lib/useCommunityBooking';
import { useTheme } from '../../../../theme/useTheme';

const EMPTY_SLOTS_MESSAGE = '예약 가능한 회차가 없어요';
const SLOTS_ERROR_MESSAGE = '회차 목록을 불러오지 못했어요';
const FORBIDDEN_MESSAGE = '방장만 연결할 수 있어요';
const GENERIC_FAILURE_MESSAGE = '연결에 실패했어요. 잠시 후 다시 시도해주세요';

interface SlotOptionProps {
  slot: SlotResponse;
  isSelected: boolean;
  onSelect: () => void;
}

function SlotOption({ slot, isSelected, onSelect }: SlotOptionProps) {
  const { tokens } = useTheme();
  const dateLabel = new Date(slot.date).toLocaleDateString('ko-KR', {
    month: 'long',
    day: 'numeric',
  });

  return (
    <Pressable
      style={[
        styles.slot,
        { borderColor: tokens.border, backgroundColor: tokens.surface },
        isSelected && { borderColor: tokens.accent, backgroundColor: tokens.surfaceElevated },
      ]}
      onPress={onSelect}
      accessibilityRole="radio"
      accessibilityLabel={`${dateLabel} ${slot.timeRange} (정원 ${slot.capacity}명)`}
      accessibilityState={{ selected: isSelected }}
    >
      <ThemedText variant={isSelected ? 'accent' : 'primary'} style={styles.slotTimeRange}>
        {`${dateLabel} ${slot.timeRange}`}
      </ThemedText>
      <ThemedText variant="secondary" style={styles.slotCapacity}>
        {`정원 ${slot.capacity}명`}
      </ThemedText>
    </Pressable>
  );
}

export default function CommunityBookingLinkScreen() {
  const { tokens } = useTheme();
  const { id, facilityId: initialFacilityId } = useLocalSearchParams<{
    id: string;
    facilityId?: string;
  }>();
  const communityId = Number(id ?? NaN);

  const [facilityId, setFacilityId] = useState(
    typeof initialFacilityId === 'string' ? initialFacilityId : ''
  );
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const slotsQuery = useSlots(facilityId);
  const linkMutation = useLinkCommunityBooking(communityId);

  const openSlots = (slotsQuery.data ?? []).filter((slot) => slot.status !== 'CLOSED');

  function handleLink() {
    if (selectedSlotId === null) {
      return;
    }
    setErrorMessage(null);
    linkMutation.mutate(
      { slotId: selectedSlotId },
      {
        onSuccess: () => {
          router.back();
        },
        onError: (error: unknown) => {
          setErrorMessage(
            isForbiddenError(error as Error) ? FORBIDDEN_MESSAGE : GENERIC_FAILURE_MESSAGE
          );
        },
      }
    );
  }

  return (
    <ThemedView style={styles.container} background="background">
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        활동 예약 연결
      </ThemedText>

      <ThemedText variant="secondary" style={styles.label}>
        시설 ID
      </ThemedText>
      <TextInput
        value={facilityId}
        onChangeText={(text) => {
          setFacilityId(text);
          setSelectedSlotId(null);
        }}
        placeholder="시설 ID를 입력하세요"
        placeholderTextColor={tokens.textTertiary}
        accessibilityLabel="시설 ID 입력"
        style={[styles.input, { borderColor: tokens.border, color: tokens.textPrimary }]}
      />

      {facilityId.length === 0 && (
        <ThemedText variant="secondary" style={styles.hint}>
          시설을 선택하면 예약 가능한 회차가 표시돼요
        </ThemedText>
      )}

      {facilityId.length > 0 && slotsQuery.isLoading && <LoadingView variant="skeleton" />}

      {facilityId.length > 0 && !slotsQuery.isLoading && slotsQuery.isError && (
        <ErrorView message={SLOTS_ERROR_MESSAGE} onRetry={() => void slotsQuery.refetch()} />
      )}

      {facilityId.length > 0 &&
        !slotsQuery.isLoading &&
        !slotsQuery.isError &&
        openSlots.length === 0 && <EmptyState message={EMPTY_SLOTS_MESSAGE} />}

      {facilityId.length > 0 &&
        !slotsQuery.isLoading &&
        !slotsQuery.isError &&
        openSlots.length > 0 && (
          <View style={styles.slotSection}>
            <ThemedText variant="secondary" style={styles.label}>
              예약 가능 회차
            </ThemedText>
            <FlatList
              data={openSlots}
              keyExtractor={(item) => String(item.id)}
              renderItem={({ item }) => (
                <SlotOption
                  slot={item}
                  isSelected={selectedSlotId === item.id}
                  onSelect={() => setSelectedSlotId(item.id)}
                />
              )}
            />
          </View>
        )}

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

      <View style={styles.ctaArea}>
        <Button
          label="이 회차로 연결"
          onPress={handleLink}
          disabled={selectedSlotId === null || linkMutation.isPending}
          loading={linkMutation.isPending}
        />
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 60,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 20,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    marginBottom: 16,
  },
  hint: {
    fontSize: 13,
    marginBottom: 24,
  },
  slotSection: {
    marginTop: 8,
  },
  slot: {
    borderWidth: 1.5,
    borderRadius: 10,
    padding: 14,
    marginBottom: 8,
  },
  slotTimeRange: {
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 2,
  },
  slotCapacity: {
    fontSize: 12,
  },
  errorText: {
    fontSize: 13,
    marginTop: 12,
  },
  ctaArea: {
    marginTop: 24,
    marginBottom: 40,
  },
});
