// @vitest-environment jsdom
import { render, act } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import * as React from "react";
import { ToastProvider, useToast } from "../toast";

function ToastCounter({ onCount }: { onCount: (n: number) => void }) {
  const { toasts, addToast } = useToast();

  React.useEffect(() => {
    onCount(toasts.length);
  }, [toasts, onCount]);

  return (
    <button
      onClick={() => addToast({ title: "알림", variant: "default" })}
      aria-label="토스트 추가"
    />
  );
}

describe("ToastProvider", () => {
  it("[U-03] 알림 큐 길이가 max(5)를 초과하면 가장 오래된 토스트가 제거된다", () => {
    const counts: number[] = [];
    const onCount = (n: number) => counts.push(n);

    const { getByLabelText } = render(
      <ToastProvider>
        <ToastCounter onCount={onCount} />
      </ToastProvider>
    );

    const btn = getByLabelText("토스트 추가");

    // 6번 추가
    for (let i = 0; i < 6; i++) {
      act(() => {
        btn.click();
      });
    }

    // 마지막 카운트는 최대 5를 초과하지 않아야 함
    const lastCount = counts[counts.length - 1] ?? 0;
    expect(lastCount).toBeLessThanOrEqual(5);
  });

  it("토스트를 추가하면 큐에 쌓인다", () => {
    const counts: number[] = [];
    const onCount = (n: number) => counts.push(n);

    const { getByLabelText } = render(
      <ToastProvider>
        <ToastCounter onCount={onCount} />
      </ToastProvider>
    );

    const btn = getByLabelText("토스트 추가");

    act(() => {
      btn.click();
    });
    act(() => {
      btn.click();
    });

    const lastCount = counts[counts.length - 1] ?? 0;
    expect(lastCount).toBe(2);
  });
});
