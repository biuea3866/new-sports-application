/**
 * 소모임 예약 연결 화면 — A-B2 (방장 전용)
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * A-B2(토스 슬롯 선택 패턴, `useSlots` 재사용) · "화면별 4상태 표" A-B2.
 *
 * 시설은 목록에서 고른다(`FacilityPicker`) — 사용자가 시설 내부 식별자를 알 리 없으므로
 * "시설 ID를 입력하세요" 입력창을 두지 않는다. BE에 키워드 검색 API가 없어(`GET /facilities`는
 * 지역·종류 필터만 지원) 이름 검색은 불러온 목록에 대한 클라이언트 필터로 처리한다.
 * 시설 목록·상세에서 진입할 때는 `facilityId` 쿼리 파라미터로 선진입도 지원한다.
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import type { FacilityResponse, SlotResponse } from '../../../../api/types';
import {
  Button,
  EmptyState,
  ErrorView,
  LoadingView,
  ThemedText,
  ThemedView,
} from '../../../../components/ui';
import { FacilityPicker } from '../../../../components/facility/FacilityPicker';
import { isForbiddenError } from '../../../../lib/http-error';
import { useSlots } from '../../../../lib/useBooking';
import { useFacilityOptions } from '../../../../lib/useFacility';
import { useLinkCommunityBooking } from '../../../../lib/useCommunityBooking';
import { useTheme } from '../../../../theme/useTheme';

const EMPTY_SLOTS_MESSAGE = '예약 가능한 회차가 없어요';
const SLOTS_ERROR_MESSAGE = '회차 목록을 불러오지 못했어요';
const FORBIDDEN_MESSAGE = '방장만 연결할 수 있어요';
const GENERIC_FAILURE_MESSAGE = '연결에 실패했어요. 잠시 후 다시 시도해주세요';
const PICKER_HINT = '시설을 선택하면 예약 가능한 회차가 표시돼요';

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
  const { id, facilityId: initialFacilityId } = useLocalSearchParams<{
    id: string;
    facilityId?: string;
  }>();
  const communityId = Number(id ?? NaN);

  const facilityOptionsQuery = useFacilityOptions();
  const [pickedFacilityId, setPickedFacilityId] = useState<string | null>(
    typeof initialFacilityId === 'string' && initialFacilityId.length > 0 ? initialFacilityId : null
  );
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedFacility =
    pickedFacilityId === null
      ? undefined
      : (facilityOptionsQuery.data ?? []).find((facility) => facility.id === pickedFacilityId);

  const slotsQuery = useSlots(pickedFacilityId ?? '');
  const linkMutation = useLinkCommunityBooking(communityId);

  const openSlots = (slotsQuery.data ?? []).filter((slot) => slot.status !== 'CLOSED');

  function handlePickFacility(facility: FacilityResponse) {
    setPickedFacilityId(facility.id);
    setSelectedSlotId(null);
  }

  function handleResetFacility() {
    setPickedFacilityId(null);
    setSelectedSlotId(null);
  }

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

      {pickedFacilityId === null ? (
        <View style={styles.pickerSection}>
          <ThemedText variant="secondary" style={styles.hint}>
            {PICKER_HINT}
          </ThemedText>
          <FacilityPicker
            facilities={facilityOptionsQuery.data}
            isLoading={facilityOptionsQuery.isLoading}
            isError={facilityOptionsQuery.isError}
            onRetry={() => void facilityOptionsQuery.refetch()}
            onSelect={handlePickFacility}
          />
        </View>
      ) : (
        <>
          <View style={styles.selectedFacilityRow}>
            <View style={styles.selectedFacilityText}>
              <ThemedText variant="secondary" style={styles.label}>
                선택한 시설
              </ThemedText>
              <ThemedText variant="primary" style={styles.selectedFacilityName} numberOfLines={1}>
                {selectedFacility?.name ?? '선택한 시설'}
              </ThemedText>
            </View>
            <Pressable
              onPress={handleResetFacility}
              accessibilityRole="button"
              accessibilityLabel="시설 다시 선택"
            >
              <ThemedText variant="accent" style={styles.resetLabel}>
                변경
              </ThemedText>
            </Pressable>
          </View>

          {slotsQuery.isLoading && <LoadingView variant="skeleton" />}

          {!slotsQuery.isLoading && slotsQuery.isError && (
            <ErrorView message={SLOTS_ERROR_MESSAGE} onRetry={() => void slotsQuery.refetch()} />
          )}

          {!slotsQuery.isLoading && !slotsQuery.isError && openSlots.length === 0 && (
            <EmptyState message={EMPTY_SLOTS_MESSAGE} />
          )}

          {!slotsQuery.isLoading && !slotsQuery.isError && openSlots.length > 0 && (
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
        </>
      )}
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
  hint: {
    fontSize: 13,
    marginBottom: 12,
  },
  pickerSection: {
    flex: 1,
  },
  selectedFacilityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  selectedFacilityText: {
    flex: 1,
    marginRight: 12,
  },
  selectedFacilityName: {
    fontSize: 16,
    fontWeight: '600',
  },
  resetLabel: {
    fontSize: 14,
    fontWeight: '600',
  },
  slotSection: {
    marginTop: 8,
    flex: 1,
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
