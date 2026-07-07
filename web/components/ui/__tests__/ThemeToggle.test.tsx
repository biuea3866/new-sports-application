// @vitest-environment jsdom
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { ThemeToggle } from "../ThemeToggle";

function mockMatchMedia(matches: boolean): void {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// Node(v22+)가 globalThis에 실험적 웹스토리지 접근자를 등록해 jsdom의 기본
// window.localStorage(.clear 등 일부 메서드 결손)를 가리는 환경 이슈가 있다.
// 테스트 격리를 위해 순수 인메모리 Storage로 명시 스텁한다.
function createMemoryStorage(): Storage {
  const store = new Map<string, string>();
  return {
    getItem: (key: string) => (store.has(key) ? store.get(key) ?? null : null),
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  };
}

describe("ThemeToggle", () => {
  beforeEach(() => {
    document.documentElement.classList.remove("dark");
    vi.stubGlobal("localStorage", createMemoryStorage());
    mockMatchMedia(false);
  });

  afterEach(() => {
    cleanup();
    document.documentElement.classList.remove("dark");
    vi.unstubAllGlobals();
  });

  it("클릭 시 .dark 클래스를 토글한다", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    expect(document.documentElement.classList.contains("dark")).toBe(false);

    await user.click(screen.getByRole("button", { name: "다크 모드로 전환" }));
    expect(document.documentElement.classList.contains("dark")).toBe(true);

    await user.click(screen.getByRole("button", { name: "라이트 모드로 전환" }));
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });

  it("클릭 후 선택값이 localStorage에 영속된다", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    await user.click(screen.getByRole("button", { name: "다크 모드로 전환" }));

    expect(window.localStorage.getItem("theme")).toBe("dark");
  });

  it("localStorage에 값이 없으면 시스템 기본(다크 선호)을 반영한다", () => {
    mockMatchMedia(true);
    render(<ThemeToggle />);

    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(screen.getByRole("button", { name: "라이트 모드로 전환" })).toBeInTheDocument();
  });

  it("localStorage에 저장된 값이 시스템 설정보다 우선한다", () => {
    window.localStorage.setItem("theme", "light");
    mockMatchMedia(true); // 시스템은 다크 선호이지만 저장된 값이 우선해야 한다

    render(<ThemeToggle />);

    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });
});
