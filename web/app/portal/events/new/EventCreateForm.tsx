"use client";

import { useState, FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CreateEventInputSchema } from "@/lib/portal/schemas";

interface FormFields {
  title: string;
  venue: string;
  startsAt: string;
  seatsText: string;
}

interface FieldErrors {
  title?: string;
  venue?: string;
  startsAt?: string;
  seats?: string;
  _form?: string;
}

/** CSV 텍스트를 파싱해 좌석 레이블 배열로 변환한다. 빈 줄은 무시한다. */
function parseSeats(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export default function EventCreateForm() {
  const router = useRouter();

  const [fields, setFields] = useState<FormFields>({
    title: "",
    venue: "",
    startsAt: "",
    seatsText: "",
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  function setField(key: keyof FormFields, value: string) {
    setFields((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key === "seatsText" ? "seats" : key]: undefined }));
  }

  function validate(): boolean {
    const seats = parseSeats(fields.seatsText);
    const parsed = CreateEventInputSchema.safeParse({
      title: fields.title,
      venue: fields.venue,
      startsAt: fields.startsAt,
      seats,
    });

    if (parsed.success) {
      setErrors({});
      return true;
    }

    const fieldErrors = parsed.error.flatten().fieldErrors;
    setErrors({
      title: fieldErrors["title"]?.[0],
      venue: fieldErrors["venue"]?.[0],
      startsAt: fieldErrors["startsAt"]?.[0],
      seats: fieldErrors["seats"]?.[0],
    });
    return false;
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!validate()) return;

    const seats = parseSeats(fields.seatsText);
    setIsSubmitting(true);
    setErrors({});

    try {
      const res = await fetch("/api/portal/events", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: fields.title,
          venue: fields.venue,
          startsAt: new Date(fields.startsAt).toISOString(),
          seats,
        }),
      });

      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setErrors({ _form: body.message ?? "경기 등록에 실패했습니다." });
        return;
      }

      const created = (await res.json()) as { id: number };
      router.push(`/portal/events/${created.id}`);
    } catch {
      setErrors({ _form: "네트워크 오류가 발생했습니다." });
    } finally {
      setIsSubmitting(false);
    }
  }

  const seatCount = parseSeats(fields.seatsText).length;
  const seatCountOver = seatCount > 500;

  return (
    <form onSubmit={(e) => void handleSubmit(e)} noValidate aria-label="경기 등록 폼">
      {errors._form !== undefined && (
        <div
          className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive mb-4"
          role="alert"
        >
          {errors._form}
        </div>
      )}

      <div className="space-y-5">
        <div>
          <label htmlFor="event-title" className="block text-sm font-medium mb-1">
            경기 제목 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="event-title"
            type="text"
            value={fields.title}
            onChange={(e) => setField("title", e.target.value)}
            placeholder="2026 K리그 결승전"
            aria-required="true"
            aria-describedby={errors.title !== undefined ? "event-title-error" : undefined}
            aria-invalid={errors.title !== undefined}
          />
          {errors.title !== undefined && (
            <p id="event-title-error" className="text-xs text-destructive mt-1" role="alert">
              {errors.title}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="event-venue" className="block text-sm font-medium mb-1">
            경기장 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="event-venue"
            type="text"
            value={fields.venue}
            onChange={(e) => setField("venue", e.target.value)}
            placeholder="서울월드컵경기장"
            aria-required="true"
            aria-describedby={errors.venue !== undefined ? "event-venue-error" : undefined}
            aria-invalid={errors.venue !== undefined}
          />
          {errors.venue !== undefined && (
            <p id="event-venue-error" className="text-xs text-destructive mt-1" role="alert">
              {errors.venue}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="event-starts-at" className="block text-sm font-medium mb-1">
            경기 시작 시각 <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <Input
            id="event-starts-at"
            type="datetime-local"
            value={fields.startsAt}
            onChange={(e) => setField("startsAt", e.target.value)}
            aria-required="true"
            aria-describedby={errors.startsAt !== undefined ? "event-starts-at-error" : undefined}
            aria-invalid={errors.startsAt !== undefined}
          />
          {errors.startsAt !== undefined && (
            <p id="event-starts-at-error" className="text-xs text-destructive mt-1" role="alert">
              {errors.startsAt}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="event-seats" className="block text-sm font-medium mb-1">
            좌석 일괄 입력{" "}
            <span aria-hidden="true" className="text-destructive">*</span>
          </label>
          <p className="text-xs text-muted-foreground mb-2">
            좌석 레이블을 줄바꿈 또는 쉼표로 구분해서 입력하세요. 최대 500개.
          </p>
          <textarea
            id="event-seats"
            value={fields.seatsText}
            onChange={(e) => setField("seatsText", e.target.value)}
            placeholder={"A1\nA2\nA3\n또는 A1,A2,A3"}
            rows={8}
            className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none"
            aria-required="true"
            aria-describedby="event-seats-hint event-seats-count"
            aria-invalid={errors.seats !== undefined || seatCountOver}
          />
          <div className="flex justify-between items-start mt-1">
            <div>
              {errors.seats !== undefined && (
                <p
                  id="event-seats-hint"
                  className="text-xs text-destructive"
                  role="alert"
                >
                  {errors.seats}
                </p>
              )}
            </div>
            <p
              id="event-seats-count"
              className={`text-xs ${seatCountOver ? "text-destructive font-medium" : "text-muted-foreground"}`}
              aria-live="polite"
            >
              {seatCount} / 500석
              {seatCountOver && " — 500개를 초과했습니다"}
            </p>
          </div>
        </div>
      </div>

      <div className="flex gap-3 mt-8">
        <Button
          type="submit"
          disabled={isSubmitting || seatCountOver}
          aria-disabled={isSubmitting || seatCountOver}
          className="flex-1"
        >
          {isSubmitting ? "등록 중..." : "경기 등록"}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => router.push("/portal/events")}
          aria-label="등록 취소하고 목록으로 돌아가기"
        >
          취소
        </Button>
      </div>
    </form>
  );
}
