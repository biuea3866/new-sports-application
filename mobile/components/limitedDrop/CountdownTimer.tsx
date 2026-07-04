/**
 * CountdownTimer — 판매 시작(openAt)까지 남은 시간을 HH:MM:SS로 표시하는 프레젠테이션 컴포넌트.
 * remainingMs는 상위(useCountdown)로부터 props로 전달받으며, 이 컴포넌트는 로직 없이 표시만 합니다.
 * remainingMs가 0 이하이면 "오픈"으로 표기합니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { ThemedText } from '../themed/ThemedText';

export interface CountdownTimerProps {
  remainingMs: number;
}

const HOUR_MS = 60 * 60 * 1000;
const MINUTE_MS = 60 * 1000;
const SECOND_MS = 1000;

function padTwoDigits(value: number): string {
  return value.toString().padStart(2, '0');
}

function formatRemaining(remainingMs: number): string {
  const hours = Math.floor(remainingMs / HOUR_MS);
  const minutes = Math.floor((remainingMs % HOUR_MS) / MINUTE_MS);
  const seconds = Math.floor((remainingMs % MINUTE_MS) / SECOND_MS);

  return `${padTwoDigits(hours)}:${padTwoDigits(minutes)}:${padTwoDigits(seconds)}`;
}

export function CountdownTimer({ remainingMs }: CountdownTimerProps) {
  const isOpen = remainingMs <= 0;
  const displayText = isOpen ? '오픈' : formatRemaining(remainingMs);

  return (
    <ThemedText
      variant="accent"
      accessibilityLiveRegion="polite"
      accessibilityLabel="판매 시작까지 남은 시간"
    >
      {displayText}
    </ThemedText>
  );
}
