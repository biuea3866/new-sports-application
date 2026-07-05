"use client";

import * as React from "react";
import { useRouter, useParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/toast";
import { FacilityForm } from "@/app/portal/facilities/_components/FacilityForm";
import { AirQualityCard } from "@/app/portal/facilities/_components/AirQualityCard";
import { useAirQuality } from "@/app/portal/facilities/_hooks/useAirQuality";
import { resolveSidoDisplayName } from "@/app/portal/facilities/sido-display";
import { parseLocation } from "@/app/portal/facilities/parse-location";
import type { MyFacility } from "@/lib/portal/types";
import type { FacilityFormValues } from "@/app/portal/facilities/facility-form-schema";

const FACILITY_TYPE_LABELS: Record<string, string> = {
  INDOOR: "실내",
  OUTDOOR: "야외",
  MIXED: "복합",
};

type ViewMode = "detail" | "edit";

interface DeleteConfirmProps {
  facilityName: string;
  isDeleting: boolean;
  deleteError: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

function DeleteConfirmDialog({
  facilityName,
  isDeleting,
  deleteError,
  onConfirm,
  onCancel,
}: DeleteConfirmProps) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-dialog-title"
      aria-describedby="delete-dialog-description"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    >
      <div className="bg-background border rounded-lg p-6 max-w-sm w-full mx-4 space-y-4">
        <h2 id="delete-dialog-title" className="text-lg font-semibold">
          시설 삭제
        </h2>
        <p id="delete-dialog-description" className="text-sm text-muted-foreground">
          <strong>{facilityName}</strong>을(를) 삭제하시겠습니까?
          <br />
          이 작업은 되돌릴 수 없습니다.
        </p>

        {deleteError && (
          <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
            {deleteError}
          </div>
        )}

        <div className="flex gap-2 justify-end">
          <Button
            variant="outline"
            onClick={onCancel}
            disabled={isDeleting}
            aria-label="삭제 취소"
          >
            취소
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            disabled={isDeleting}
            aria-label="삭제 확인"
          >
            {isDeleting ? "삭제 중..." : "삭제"}
          </Button>
        </div>
      </div>
    </div>
  );
}

export default function FacilityDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { addToast } = useToast();

  const [facility, setFacility] = React.useState<MyFacility | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [loadError, setLoadError] = React.useState<string | null>(null);

  const [viewMode, setViewMode] = React.useState<ViewMode>("detail");
  const [isUpdating, setIsUpdating] = React.useState(false);
  const [updateError, setUpdateError] = React.useState<string | null>(null);

  const [showDeleteConfirm, setShowDeleteConfirm] = React.useState(false);
  const [isDeleting, setIsDeleting] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState<string | null>(null);

  const parsedLocation = parseLocation(facility?.location);
  const { status: airQualityStatus, data: airQualityData } = useAirQuality(
    parsedLocation?.lat,
    parsedLocation?.lng
  );

  React.useEffect(() => {
    async function fetchFacility() {
      try {
        const response = await fetch(`/api/portal/facilities/${id}`);
        if (!response.ok) {
          const body = (await response.json()) as { message?: string };
          setLoadError(body.message ?? "시설 정보를 불러오지 못했습니다.");
          return;
        }
        const data = (await response.json()) as MyFacility;
        setFacility(data);
      } catch {
        setLoadError("네트워크 오류가 발생했습니다.");
      } finally {
        setLoading(false);
      }
    }
    void fetchFacility();
  }, [id]);

  async function handleUpdate(values: FacilityFormValues) {
    setIsUpdating(true);
    setUpdateError(null);

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { code: _code, ...patch } = values;

    try {
      const response = await fetch(`/api/portal/facilities/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(patch),
      });

      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        setUpdateError(body.message ?? "수정 중 오류가 발생했습니다.");
        return;
      }

      const updated = (await response.json()) as MyFacility;
      setFacility(updated);
      setViewMode("detail");
      addToast({ title: "시설 정보가 수정됐습니다.", variant: "default" });
    } catch {
      setUpdateError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsUpdating(false);
    }
  }

  async function handleDelete() {
    setIsDeleting(true);
    setDeleteError(null);

    try {
      const response = await fetch(`/api/portal/facilities/${id}`, { method: "DELETE" });

      if (response.status === 409) {
        const body = (await response.json()) as { message?: string; detail?: string };
        setDeleteError(
          body.detail ?? "활성 슬롯이 있는 시설은 삭제할 수 없습니다."
        );
        return;
      }

      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        setDeleteError(body.message ?? "삭제 중 오류가 발생했습니다.");
        return;
      }

      addToast({ title: "시설이 삭제됐습니다.", variant: "default" });
      router.push("/portal/facilities");
    } catch {
      setDeleteError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsDeleting(false);
    }
  }

  if (loading) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-8">
        <p className="text-sm text-muted-foreground" aria-live="polite" aria-busy="true">
          불러오는 중...
        </p>
      </main>
    );
  }

  if (loadError || !facility) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-8 space-y-4">
        <div role="alert" className="rounded-md border border-destructive p-4 text-sm text-destructive">
          {loadError ?? "시설 정보를 찾을 수 없습니다."}
        </div>
        <Button variant="outline" onClick={() => router.push("/portal/facilities")} aria-label="목록으로 돌아가기">
          목록으로
        </Button>
      </main>
    );
  }

  return (
    <>
      {showDeleteConfirm && (
        <DeleteConfirmDialog
          facilityName={facility.name}
          isDeleting={isDeleting}
          deleteError={deleteError}
          onConfirm={() => void handleDelete()}
          onCancel={() => {
            setShowDeleteConfirm(false);
            setDeleteError(null);
          }}
        />
      )}

      <main className="max-w-2xl mx-auto px-4 py-8 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight">{facility.name}</h1>
          <div className="flex gap-2">
            {viewMode === "detail" && (
              <>
                <Button
                  variant="outline"
                  onClick={() => setViewMode("edit")}
                  aria-label={`${facility.name} 수정`}
                >
                  수정
                </Button>
                <Button
                  variant="destructive"
                  onClick={() => setShowDeleteConfirm(true)}
                  aria-label={`${facility.name} 삭제`}
                >
                  삭제
                </Button>
              </>
            )}
            <Button
              variant="ghost"
              onClick={() => router.push("/portal/facilities")}
              aria-label="목록으로 돌아가기"
            >
              목록
            </Button>
          </div>
        </div>

        {viewMode === "detail" ? (
          <section aria-label="시설 상세 정보" className="rounded-md border p-6 space-y-4">
            <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
              <div>
                <dt className="font-medium text-muted-foreground">코드</dt>
                <dd className="mt-1">{facility.code}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">유형</dt>
                <dd className="mt-1">
                  <Badge variant="secondary">
                    {FACILITY_TYPE_LABELS[facility.type] ?? facility.type}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">시/도</dt>
                <dd className="mt-1">{resolveSidoDisplayName(facility.sidoName)}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">구</dt>
                <dd className="mt-1">{facility.gu}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">주차</dt>
                <dd className="mt-1">
                  <Badge variant={facility.parking ? "default" : "outline"}>
                    {facility.parking ? "가능" : "불가"}
                  </Badge>
                </dd>
              </div>
              <div className="col-span-2">
                <dt className="font-medium text-muted-foreground">주소</dt>
                <dd className="mt-1">{facility.address}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">위치 좌표</dt>
                <dd className="mt-1">{facility.location}</dd>
              </div>
              <div>
                <dt className="font-medium text-muted-foreground">전화번호</dt>
                <dd className="mt-1">{facility.tel}</dd>
              </div>
              {facility.homePage && (
                <div className="col-span-2">
                  <dt className="font-medium text-muted-foreground">홈페이지</dt>
                  <dd className="mt-1">
                    <a
                      href={facility.homePage}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary hover:underline"
                      aria-label={`${facility.name} 홈페이지 열기 (새 탭)`}
                    >
                      {facility.homePage}
                    </a>
                  </dd>
                </div>
              )}
              <div>
                <dt className="font-medium text-muted-foreground">교육 여부</dt>
                <dd className="mt-1">{facility.eduYn ? "예" : "아니오"}</dd>
              </div>
              {facility.meta && (
                <div className="col-span-2">
                  <dt className="font-medium text-muted-foreground">메타 정보</dt>
                  <dd className="mt-1 whitespace-pre-wrap">{facility.meta}</dd>
                </div>
              )}
            </dl>

            {airQualityStatus !== "idle" && (
              <div className="border-t pt-4">
                <AirQualityCard status={airQualityStatus} data={airQualityData} />
              </div>
            )}
          </section>
        ) : (
          <section aria-label="시설 수정">
            {updateError && (
              <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive mb-4">
                {updateError}
              </div>
            )}
            <FacilityForm
              mode="edit"
              defaultValues={{
                code: facility.code,
                name: facility.name,
                sido: facility.sidoCode,
                gu: facility.gu,
                type: facility.type,
                address: facility.address,
                location: facility.location,
                parking: facility.parking,
                tel: facility.tel,
                homePage: facility.homePage ?? "",
                eduYn: facility.eduYn,
                meta: facility.meta ?? "",
              }}
              isSubmitting={isUpdating}
              onSubmit={(v) => { void handleUpdate(v); }}
              onCancel={() => {
                setViewMode("detail");
                setUpdateError(null);
              }}
            />
          </section>
        )}
      </main>
    </>
  );
}
