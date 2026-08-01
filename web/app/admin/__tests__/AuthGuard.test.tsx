// @vitest-environment jsdom
/**
 * AuthGuard 리다이렉트 정책 테스트.
 *
 * layout이 실제 세션을 읽도록 바뀌면서 `isAuthenticated`가 처음으로 false가 될 수 있게 됐다.
 * 비-prod 미리보기(`NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED`)는 캡쳐·로컬 확인용으로 세션 없이 통과시키되,
 * production에서는 세션이 없으면 반드시 차단해야 한다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render } from "@testing-library/react";
import { AuthGuard } from "../_components/AuthGuard";

const redirectMock = vi.fn();

vi.mock("next/navigation", () => ({
  redirect: (url: string) => {
    redirectMock(url);
    throw new Error("NEXT_REDIRECT");
  },
}));

const ORIGINAL_ENV = { ...process.env };

function renderGuard(isAuthenticated: boolean): void {
  // AuthGuard 는 env 를 모듈 로드 시점이 아니라 렌더 시점에 읽으므로 정적 import 로 충분하다.
  try {
    render(<AuthGuard isAuthenticated={isAuthenticated}>본문</AuthGuard>);
  } catch (error) {
    if (!(error instanceof Error) || error.message !== "NEXT_REDIRECT") throw error;
  }
}

function setEnv(nodeEnv: string, previewEnabled: string | undefined): void {
  vi.stubEnv("NODE_ENV", nodeEnv);
  if (previewEnabled === undefined) {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED", "");
  } else {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED", previewEnabled);
  }
}

describe("AuthGuard", () => {
  beforeEach(() => {
    redirectMock.mockClear();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    process.env = { ...ORIGINAL_ENV };
  });

  it("인증된 운영자는 비-prod에서 본문을 볼 수 있다", () => {
    setEnv("development", undefined);

    renderGuard(true);

    expect(redirectMock).not.toHaveBeenCalled();
  });

  it("비-prod 미인증은 로그인으로 리다이렉트한다", () => {
    setEnv("development", undefined);

    renderGuard(false);

    expect(redirectMock).toHaveBeenCalledWith("/login?redirect=/admin");
  });

  it("비-prod 미리보기 모드에서는 세션 없이도 화면을 확인할 수 있다", () => {
    setEnv("development", "true");

    renderGuard(false);

    expect(redirectMock).not.toHaveBeenCalled();
  });

  it("production에서는 미리보기 플래그가 켜져 있어도 미인증을 차단한다", () => {
    setEnv("production", "true");

    renderGuard(false);

    expect(redirectMock).toHaveBeenCalledWith("/login?redirect=/admin");
  });

  it("production에서 미리보기 플래그가 없으면 어드민 라우트 자체를 차단한다", () => {
    setEnv("production", undefined);

    renderGuard(true);

    expect(redirectMock).toHaveBeenCalledWith("/login?redirect=/admin");
  });
});
