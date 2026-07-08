"use client";

/**
 * /portal/slots — 슬롯 캘린더 뷰 + CRUD
 * FACILITY_OWNER가 시설별 슬롯을 등록·수정·삭제한다.
 */
import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import {
  type SlotResponse,
  type CreateSlotInput,
  type UpdateSlotInput,
  createSlot,
  closeSlot,
  deleteSlot,
  fetchSlots,
  openSlot,
  updateSlot,
} from "@/lib/portal/slots";
import { cn } from "@/lib/utils";
import { SlotRow } from "./_components/SlotRow";
import { SlotCloseConfirmDialog } from "./_components/SlotCloseConfirmDialog";

interface FacilityOption {
  id: string;
  name: string;
}

// 달력 표시용 날짜 유틸
function getDaysInMonth(year: number, month: number): Date[] {
  const days: Date[] = [];
  const date = new Date(year, month, 1);
  while (date.getMonth() === month) {
    days.push(new Date(date));
    date.setDate(date.getDate() + 1);
  }
  return days;
}

function formatDate(date: Date): string {
  const y = date.getFullYear();
  const mo = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${mo}-${d}`;
}

function isSameDay(a: Date, b: Date): boolean {
  return formatDate(a) === formatDate(b);
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

interface SlotModalState {
  mode: "create" | "edit";
  date?: Date;
  slot?: SlotResponse;
}

interface SlotFormValues {
  date: string;
  timeRange: string;
  capacity: string;
}

const DEFAULT_FORM: SlotFormValues = { date: "", timeRange: "", capacity: "" };

export default function SlotsPage() {
  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth());
  const [facilities, setFacilities] = useState<FacilityOption[]>([]);
  const [facilityId, setFacilityId] = useState("");
  const [slots, setSlots] = useState<SlotResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalState, setModalState] = useState<SlotModalState | null>(null);
  const [form, setForm] = useState<SlotFormValues>(DEFAULT_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [deletingSlot, setDeletingSlot] = useState<SlotResponse | null>(null);
  const [closingSlot, setClosingSlot] = useState<SlotResponse | null>(null);
  const [closeSubmitting, setCloseSubmitting] = useState(false);
  const [openingSlotId, setOpeningSlotId] = useState<number | null>(null);
  const { addToast } = useToast();

  useEffect(() => {
    fetch("/api/portal/facilities?page=0&size=100")
      .then((r) => r.json())
      .then((data: { content: FacilityOption[] }) => {
        setFacilities(data.content);
        if (data.content.length > 0 && data.content[0]) {
          setFacilityId(data.content[0].id);
        }
      })
      .catch(() => {
        setError("시설 목록을 불러오지 못했습니다.");
      });
  }, []);

  const loadSlots = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await fetchSlots(facilityId);
      setSlots(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "슬롯 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    void loadSlots();
  }, [loadSlots]);

  const daysInMonth = getDaysInMonth(year, month);

  // 달력 첫 번째 날의 요일 오프셋(일요일=0)
  const startOffset = daysInMonth[0]?.getDay() ?? 0;
  const calendarCells = Array.from<Date | null>({ length: startOffset }).fill(null).concat(daysInMonth);

  function openCreateModal(day: Date) {
    setForm({ date: formatDate(day), timeRange: "", capacity: "" });
    setSubmitError(null);
    setModalState({ mode: "create", date: day });
  }

  function openEditModal(slot: SlotResponse) {
    setForm({
      date: slot.date.slice(0, 10),
      timeRange: slot.timeRange,
      capacity: String(slot.capacity),
    });
    setSubmitError(null);
    setModalState({ mode: "edit", slot });
  }

  function closeModal() {
    setModalState(null);
    setForm(DEFAULT_FORM);
    setSubmitError(null);
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!facilityId || !modalState) return;

    const capacityNum = parseInt(form.capacity, 10);
    if (isNaN(capacityNum) || capacityNum <= 0) {
      setSubmitError("인원 수는 1 이상의 숫자여야 합니다.");
      return;
    }
    if (!form.timeRange.trim()) {
      setSubmitError("시간대를 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      if (modalState.mode === "create") {
        const input: CreateSlotInput = {
          date: new Date(`${form.date}T00:00:00`).toISOString(),
          timeRange: form.timeRange.trim(),
          capacity: capacityNum,
        };
        await createSlot(facilityId, input);
      } else if (modalState.slot) {
        const input: UpdateSlotInput = {
          timeRange: form.timeRange.trim(),
          capacity: capacityNum,
        };
        await updateSlot(facilityId, modalState.slot.id, input);
      }
      closeModal();
      await loadSlots();
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmDelete() {
    if (!deletingSlot) return;
    try {
      await deleteSlot(facilityId, deletingSlot.id);
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "삭제에 실패했습니다.");
    } finally {
      setDeletingSlot(null);
    }
  }

  async function confirmClose() {
    if (!closingSlot) return;
    setCloseSubmitting(true);
    try {
      await closeSlot(facilityId, closingSlot.id);
      await loadSlots();
      addToast({ title: "슬롯이 마감됐습니다.", variant: "default" });
      setClosingSlot(null);
    } catch (err) {
      addToast({
        title: err instanceof Error ? err.message : "슬롯 마감에 실패했습니다.",
        variant: "destructive",
      });
    } finally {
      setCloseSubmitting(false);
    }
  }

  async function handleOpen(slot: SlotResponse) {
    setOpeningSlotId(slot.id);
    try {
      await openSlot(facilityId, slot.id);
      await loadSlots();
      addToast({ title: "슬롯이 오픈됐습니다.", variant: "default" });
    } catch (err) {
      addToast({
        title: err instanceof Error ? err.message : "슬롯 오픈에 실패했습니다.",
        variant: "destructive",
      });
    } finally {
      setOpeningSlotId(null);
    }
  }

  function prevMonth() {
    if (month === 0) {
      setYear((y) => y - 1);
      setMonth(11);
    } else {
      setMonth((m) => m - 1);
    }
  }

  function nextMonth() {
    if (month === 11) {
      setYear((y) => y + 1);
      setMonth(0);
    } else {
      setMonth((m) => m + 1);
    }
  }

  const currentMonthLabel = `${year}년 ${month + 1}월`;

  return (
    <main className="min-h-screen p-6 space-y-6">
      <h1 className="text-2xl font-bold">슬롯 관리</h1>

      {/* 시설 선택 */}
      <section aria-label="시설 선택">
        <label htmlFor="facility-select" className="block text-sm font-medium mb-1">
          시설 선택
        </label>
        <select
          id="facility-select"
          value={facilityId}
          onChange={(e) => setFacilityId(e.target.value)}
          className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label="시설 선택"
        >
          {facilities.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
      </section>

      {/* 월 네비게이션 */}
      <nav aria-label="달력 월 이동" className="flex items-center gap-4">
        <Button
          variant="outline"
          size="sm"
          onClick={prevMonth}
          aria-label="이전 달"
        >
          &lt;
        </Button>
        <span className="text-lg font-semibold" aria-live="polite" aria-atomic="true">
          {currentMonthLabel}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={nextMonth}
          aria-label="다음 달"
        >
          &gt;
        </Button>
      </nav>

      {/* 오류 */}
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-gray-500">
          슬롯 목록을 불러오는 중...
        </p>
      )}

      {/* 캘린더 */}
      <section aria-label={`${currentMonthLabel} 슬롯 캘린더`}>
        {/* 요일 헤더 */}
        <div
          className="grid grid-cols-7 gap-px bg-gray-200 border border-gray-200 rounded-t-md overflow-hidden"
          role="row"
          aria-label="요일"
        >
          {WEEKDAYS.map((day) => (
            <div
              key={day}
              role="columnheader"
              className="bg-gray-50 text-center text-xs font-medium text-gray-600 py-2"
            >
              {day}
            </div>
          ))}
        </div>

        {/* 날짜 셀 */}
        <div
          className="grid grid-cols-7 gap-px bg-gray-200 border-x border-b border-gray-200 rounded-b-md overflow-hidden"
          role="grid"
          aria-label={`${currentMonthLabel} 캘린더`}
        >
          {calendarCells.map((day, idx) => {
            if (!day) {
              return (
                <div
                  key={`empty-${idx}`}
                  role="gridcell"
                  aria-hidden="true"
                  className="bg-gray-50 min-h-[80px]"
                />
              );
            }

            const daySlots = slots.filter((s) => isSameDay(new Date(s.date), day));
            const isToday = isSameDay(day, today);
            const dayLabel = `${month + 1}월 ${day.getDate()}일`;

            return (
              <div
                key={formatDate(day)}
                role="gridcell"
                className={cn(
                  "bg-white min-h-[80px] p-1 text-xs cursor-pointer hover:bg-blue-50 transition-colors",
                  isToday && "ring-2 ring-inset ring-blue-400"
                )}
                onClick={() => openCreateModal(day)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    openCreateModal(day);
                  }
                }}
                tabIndex={0}
                aria-label={`${dayLabel} - 슬롯 ${daySlots.length}개. 클릭하여 슬롯 등록`}
              >
                <span
                  className={cn(
                    "font-semibold",
                    isToday && "text-blue-600"
                  )}
                >
                  {day.getDate()}
                </span>
                <ul className="mt-1 space-y-0.5" aria-label={`${dayLabel} 슬롯 목록`}>
                  {daySlots.map((slot) => (
                    <SlotRow
                      key={slot.id}
                      slot={slot}
                      onEdit={() => openEditModal(slot)}
                      onDelete={() => setDeletingSlot(slot)}
                      onClose={() => setClosingSlot(slot)}
                      onOpen={() => void handleOpen(slot)}
                      closing={closingSlot?.id === slot.id && closeSubmitting}
                      opening={openingSlotId === slot.id}
                    />
                  ))}
                </ul>
              </div>
            );
          })}
        </div>
      </section>

      {/* 슬롯 등록/수정 모달 */}
      <Dialog open={modalState !== null} onOpenChange={(open) => !open && closeModal()}>
        <DialogContent aria-labelledby="slot-modal-title">
          <DialogHeader>
            <DialogTitle id="slot-modal-title">
              {modalState?.mode === "create" ? "슬롯 등록" : "슬롯 수정"}
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={(e) => void handleSubmit(e)} noValidate>
            <div className="space-y-4 py-2">
              <div>
                <label htmlFor="slot-date" className="block text-sm font-medium mb-1">
                  날짜
                </label>
                <Input
                  id="slot-date"
                  type="date"
                  value={form.date}
                  onChange={(e) => setForm((prev) => ({ ...prev, date: e.target.value }))}
                  required
                  aria-required="true"
                  disabled={modalState?.mode === "edit"}
                />
              </div>

              <div>
                <label htmlFor="slot-time-range" className="block text-sm font-medium mb-1">
                  시간대 <span aria-hidden="true">(예: 09:00-10:00)</span>
                </label>
                <Input
                  id="slot-time-range"
                  type="text"
                  value={form.timeRange}
                  onChange={(e) => setForm((prev) => ({ ...prev, timeRange: e.target.value }))}
                  placeholder="09:00-10:00"
                  required
                  aria-required="true"
                />
              </div>

              <div>
                <label htmlFor="slot-capacity" className="block text-sm font-medium mb-1">
                  최대 인원
                </label>
                <Input
                  id="slot-capacity"
                  type="number"
                  min={1}
                  value={form.capacity}
                  onChange={(e) => setForm((prev) => ({ ...prev, capacity: e.target.value }))}
                  required
                  aria-required="true"
                />
              </div>

              {submitError && (
                <p role="alert" className="text-sm text-red-600">
                  {submitError}
                </p>
              )}
            </div>

            <DialogFooter className="mt-4">
              <Button
                type="button"
                variant="outline"
                onClick={closeModal}
                disabled={submitting}
              >
                취소
              </Button>
              <Button type="submit" disabled={submitting} aria-busy={submitting}>
                {submitting ? "저장 중..." : "저장"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      {/* 슬롯 삭제 확인 Dialog */}
      <Dialog open={deletingSlot !== null} onOpenChange={(open) => !open && setDeletingSlot(null)}>
        <DialogContent aria-labelledby="delete-confirm-title">
          <DialogHeader>
            <DialogTitle id="delete-confirm-title">슬롯 삭제</DialogTitle>
          </DialogHeader>
          <p className="py-2 text-sm">
            슬롯({deletingSlot?.timeRange})을 삭제하시겠습니까?
          </p>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeletingSlot(null)}
            >
              취소
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => void confirmDelete()}
            >
              삭제
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {/* 슬롯 마감 확인 Dialog */}
      <SlotCloseConfirmDialog
        open={closingSlot !== null}
        timeRange={closingSlot?.timeRange ?? null}
        submitting={closeSubmitting}
        onConfirm={() => void confirmClose()}
        onCancel={() => setClosingSlot(null)}
      />
    </main>
  );
}
