"use client";

/**
 * 날짜·일시 입력 — 표시 형식을 한국식(`YYYY-MM-DD`, 24시간제)으로 직접 제어한다.
 *
 * 네이티브 `<input type="date">`·`type="datetime-local">` 은 표시 형식을 문서 `lang` 이 아니라
 * **브라우저 UI 로케일**로 정한다. 한국어 화면인데 `mm/dd/yyyy` · `mm/dd/yyyy, --:-- --` 로
 * 찍히던 결함(포털 이벤트 등록, 어드민 MCP 토큰·감사로그·사용분석)이 여기서 나왔고,
 * `lang="ko-KR"` 을 붙여도 Chromium 표시가 바뀌지 않음을 확인했다.
 *
 * 그래서 텍스트 입력으로 바꾸고 자릿수 마스킹을 직접 한다. **값 계약은 네이티브와 동일**하게
 * `yyyy-MM-dd` / `yyyy-MM-ddTHH:mm` 를 유지해 호출부(쿼리 파라미터 조립·전송)를 바꾸지 않는다.
 * 달력 선택 UX 를 잃지 않도록, 지원하는 브라우저에서는 숨은 네이티브 입력의 `showPicker()` 를
 * 여는 버튼을 함께 제공한다(달력 팝업 자체는 브라우저 UI 이며 표시 형식과 무관하다).
 */
import * as React from "react";
import { cn } from "@/lib/utils";

const INPUT_CLASS_NAME =
  "w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50";

const DATE_PLACEHOLDER = "YYYY-MM-DD";
const DATE_TIME_PLACEHOLDER = "YYYY-MM-DD HH:mm";

/** `yyyy-MM-dd` 8자리, `yyyy-MM-dd HH:mm` 12자리. */
const DATE_DIGIT_COUNT = 8;
const DATE_TIME_DIGIT_COUNT = 12;

export interface DateFieldProps {
  value: string;
  onChange: (value: string) => void;
  id?: string;
  name?: string;
  className?: string;
  disabled?: boolean;
  required?: boolean;
  /** 달력이 고를 수 있는 하한. 값 계약과 같은 형식(`yyyy-MM-dd`/`yyyy-MM-ddTHH:mm`). */
  min?: string;
  /** 달력이 고를 수 있는 상한. */
  max?: string;
  "aria-label"?: string;
  "aria-describedby"?: string;
  /** 필수 입력 여부. 네이티브 입력에서 쓰던 값을 그대로 받는다. */
  "aria-required"?: boolean;
  /** 호출부(서버 검증·폼 검증)가 판정한 오류 상태. 형식 오류와 함께 반영된다. */
  "aria-invalid"?: boolean;
}

function digitsOf(rawText: string): string {
  return rawText.replace(/\D/g, "");
}

/** 자릿수를 `YYYY-MM-DD[ HH:mm]` 모양으로 끊어 보여 준다. 미완성 입력도 그대로 표시한다. */
function maskDigits(digits: string, withTime: boolean): string {
  const year = digits.slice(0, 4);
  const month = digits.slice(4, 6);
  const day = digits.slice(6, 8);
  const hour = digits.slice(8, 10);
  const minute = digits.slice(10, 12);

  let masked = year;
  if (month.length > 0) masked += `-${month}`;
  if (day.length > 0) masked += `-${day}`;
  if (!withTime) return masked;
  if (hour.length > 0) masked += ` ${hour}`;
  if (minute.length > 0) masked += `:${minute}`;
  return masked;
}

/**
 * 달력에 실재하는 날짜인지 확인한다.
 * `2026-02-31` 처럼 자릿수만 맞는 값을 값으로 확정하면 서버가 거절할 요청을 보내게 된다.
 */
function isRealDate(year: number, month: number, day: number): boolean {
  if (month < 1 || month > 12 || day < 1) return false;
  const probe = new Date(Date.UTC(year, month - 1, day));
  return probe.getUTCFullYear() === year && probe.getUTCMonth() === month - 1 && probe.getUTCDate() === day;
}

/**
 * 입력 판정 결과.
 *
 * 네이티브 입력이 하던 검증(달력 실재 여부·`min`/`max` 범위)을 직접 하게 되면서, "왜 값이
 * 확정되지 않았는가"를 구분해야 안내 문구를 고를 수 있다. 빈 문자열 하나로 뭉개면 사용자는
 * 입력이 사라진 이유를 알 수 없다.
 */
type TemporalParseResult =
  | { kind: "empty" }
  | { kind: "incomplete" }
  | { kind: "unrealDate" }
  | { kind: "outOfRange" }
  | { kind: "confirmed"; value: string };

/** 값 계약 문자열끼리는 사전순 비교가 곧 시간순 비교다(`yyyy-MM-dd`·`yyyy-MM-ddTHH:mm`). */
function isWithinRange(contractValue: string, min?: string, max?: string): boolean {
  if (min !== undefined && min.length > 0 && contractValue < min) return false;
  if (max !== undefined && max.length > 0 && contractValue > max) return false;
  return true;
}

/**
 * 자릿수를 판정한다. 확정된 경우에만 값 계약 문자열을 돌려준다.
 *
 * `min`/`max`를 여기서 함께 본다 — 숨은 달력 입력에만 넘기면 직접 입력으로 범위 밖 날짜를
 * 그대로 확정할 수 있다(네이티브는 `:invalid` + 제출 차단이었다).
 */
function parseDigits(
  digits: string,
  withTime: boolean,
  min?: string,
  max?: string
): TemporalParseResult {
  if (digits.length === 0) return { kind: "empty" };

  const requiredCount = withTime ? DATE_TIME_DIGIT_COUNT : DATE_DIGIT_COUNT;
  if (digits.length !== requiredCount) return { kind: "incomplete" };

  const year = Number(digits.slice(0, 4));
  const month = Number(digits.slice(4, 6));
  const day = Number(digits.slice(6, 8));
  if (!isRealDate(year, month, day)) return { kind: "unrealDate" };

  const datePart = `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
  let contractValue = datePart;

  if (withTime) {
    const hour = Number(digits.slice(8, 10));
    const minute = Number(digits.slice(10, 12));
    if (hour > 23 || minute > 59) return { kind: "unrealDate" };
    contractValue = `${datePart}T${digits.slice(8, 10)}:${digits.slice(10, 12)}`;
  }

  if (!isWithinRange(contractValue, min, max)) return { kind: "outOfRange" };

  return { kind: "confirmed", value: contractValue };
}

/** 판정 결과별 안내 문구. `empty`·`confirmed`는 알릴 것이 없다. */
function validationMessageOf(
  result: TemporalParseResult,
  withTime: boolean,
  min?: string,
  max?: string
): string | null {
  switch (result.kind) {
    case "incomplete":
      return withTime
        ? "날짜와 시각을 YYYY-MM-DD HH:mm 형식으로 모두 입력해 주세요."
        : "날짜를 YYYY-MM-DD 형식으로 모두 입력해 주세요.";
    case "unrealDate":
      return "없는 날짜입니다. 다시 확인해 주세요.";
    case "outOfRange": {
      if (min !== undefined && min.length > 0 && max !== undefined && max.length > 0) {
        return `${min} ~ ${max} 사이로 입력해 주세요.`;
      }
      if (min !== undefined && min.length > 0) return `${min} 이후로 입력해 주세요.`;
      if (max !== undefined && max.length > 0) return `${max} 이전으로 입력해 주세요.`;
      return "입력할 수 있는 범위를 벗어났습니다.";
    }
    default:
      return null;
  }
}

/** 값 계약 문자열(`yyyy-MM-dd`/`yyyy-MM-ddTHH:mm[:ss]`)을 화면 표기로 바꾼다. */
function toDisplayText(contractValue: string, withTime: boolean): string {
  if (contractValue.length === 0) return "";
  const digits = digitsOf(contractValue).slice(0, withTime ? DATE_TIME_DIGIT_COUNT : DATE_DIGIT_COUNT);
  return maskDigits(digits, withTime);
}

interface MaskedTemporalFieldProps extends DateFieldProps {
  withTime: boolean;
}

function MaskedTemporalField({
  value,
  onChange,
  withTime,
  id,
  name,
  className,
  disabled,
  required,
  min,
  max,
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
  "aria-required": ariaRequired,
  "aria-invalid": ariaInvalid,
}: MaskedTemporalFieldProps) {
  /**
   * 사용자가 실제로 입력한 문자열. 확정되지 않아도 **버리지 않는다** — blur 시 지우면 사용자가
   * `2026-07-1`까지 치고 포커스를 옮겼을 때 입력이 아무 안내 없이 사라진다(무음 데이터 손실).
   * 대신 확정 못 한 이유를 안내로 알린다.
   */
  const [editingText, setEditingText] = React.useState<string | null>(null);
  // 안내는 입력을 마친 뒤에만 띄운다 — 타이핑 중 매 글자마다 다그치지 않기 위함이다.
  const [isEditing, setIsEditing] = React.useState(false);
  const generatedId = React.useId();
  const validationMessageId = `${id ?? generatedId}-date-validation`;
  const pickerRef = React.useRef<HTMLInputElement>(null);

  const displayText = editingText ?? toDisplayText(value, withTime);
  const maxDigitCount = withTime ? DATE_TIME_DIGIT_COUNT : DATE_DIGIT_COUNT;

  const parseResult = parseDigits(digitsOf(displayText), withTime, min, max);
  const pendingMessage = validationMessageOf(parseResult, withTime, min, max);
  const validationMessage = isEditing ? null : pendingMessage;
  // 호출부가 알린 오류(서버 검증 등)와 이 컴포넌트가 찾은 형식 오류를 함께 반영한다.
  const isInvalid = ariaInvalid === true || validationMessage !== null;

  function handleTextChange(event: React.ChangeEvent<HTMLInputElement>) {
    const digits = digitsOf(event.target.value).slice(0, maxDigitCount);
    setEditingText(maskDigits(digits, withTime));
    const result = parseDigits(digits, withTime, min, max);
    onChange(result.kind === "confirmed" ? result.value : "");
  }

  function handlePickerChange(event: React.ChangeEvent<HTMLInputElement>) {
    setEditingText(null);
    onChange(event.target.value);
  }

  function openPicker() {
    const picker = pickerRef.current;
    // showPicker 미지원 브라우저에서는 텍스트 입력만으로도 값을 넣을 수 있으므로 조용히 넘어간다.
    if (picker && typeof picker.showPicker === "function") {
      picker.showPicker();
    }
  }

  const describedBy = [ariaDescribedBy, validationMessage !== null ? validationMessageId : null]
    .filter((token): token is string => token !== null && token !== undefined)
    .join(" ");

  return (
    <div className={cn("relative", className)}>
      <input
        type="text"
        inputMode="numeric"
        autoComplete="off"
        id={id}
        name={name}
        value={displayText}
        onChange={handleTextChange}
        onFocus={() => setIsEditing(true)}
        onBlur={() => setIsEditing(false)}
        disabled={disabled}
        required={required}
        aria-label={ariaLabel}
        aria-required={ariaRequired}
        aria-invalid={isInvalid}
        aria-describedby={describedBy.length > 0 ? describedBy : undefined}
        placeholder={withTime ? DATE_TIME_PLACEHOLDER : DATE_PLACEHOLDER}
        className={cn(INPUT_CLASS_NAME, "pr-9")}
      />
      <button
        type="button"
        tabIndex={-1}
        aria-hidden="true"
        disabled={disabled}
        onClick={openPicker}
        className="absolute inset-y-0 right-0 flex w-9 items-center justify-center text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
      >
        {/* 달력 아이콘 — 색은 currentColor 로 두어 라이트·다크 모두 본문색을 따른다. */}
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <rect x="3" y="4" width="18" height="18" rx="2" />
          <path d="M16 2v4M8 2v4M3 10h18" />
        </svg>
      </button>
      {/* 달력 팝업 전용 — 화면에는 보이지 않고 표시 형식에도 관여하지 않는다. */}
      <input
        ref={pickerRef}
        type={withTime ? "datetime-local" : "date"}
        tabIndex={-1}
        aria-hidden="true"
        value={value}
        min={min}
        max={max}
        onChange={handlePickerChange}
        className="pointer-events-none absolute bottom-0 right-0 h-0 w-0 opacity-0"
      />
      {validationMessage !== null && (
        <p id={validationMessageId} role="alert" className="mt-1 text-xs text-destructive">
          {validationMessage}
        </p>
      )}
    </div>
  );
}

/** 날짜 입력. 값 계약은 `yyyy-MM-dd`, 비어 있으면 `""`. */
export function DateField(props: DateFieldProps) {
  return <MaskedTemporalField {...props} withTime={false} />;
}

/** 일시 입력. 값 계약은 `yyyy-MM-ddTHH:mm`(24시간제), 비어 있으면 `""`. */
export function DateTimeField(props: DateFieldProps) {
  return <MaskedTemporalField {...props} withTime={true} />;
}
