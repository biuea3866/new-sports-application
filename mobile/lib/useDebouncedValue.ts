/**
 * useDebouncedValue — 값이 delayMs 동안 추가 변경 없이 유지되면 그 값을 반환하는 순수 디바운스 훅.
 *
 * 근거: design-fe-app.md "상태관리 설계"(keyword 입력값 = 지역 상태) +
 * FE-08 티켓 "디바운스 로직은 순수 훅/유틸로(컴포넌트 내 타이머 로직 최소화)".
 * CatalogSearchControls의 검색어 디바운스에 사용한다.
 */
import { useEffect, useState } from 'react';

export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setDebouncedValue(value);
    }, delayMs);

    return () => clearTimeout(timeoutId);
  }, [value, delayMs]);

  return debouncedValue;
}
