"use client";

/**
 * 다크 모드 토글 (W-TH, design-fe-web.md).
 * 토큰(app/globals.css .dark)은 이미 완비되어 있으나 토글이 없어 라이트만 사용 가능했다.
 * 클릭 시 document.documentElement에 .dark 클래스를 토글하고 localStorage에 영속한다.
 * 저장된 값이 없으면 시스템 기본(prefers-color-scheme)을 따른다.
 */
import * as React from "react";
import { Moon, Sun } from "lucide-react";
import { Button } from "./button";

const THEME_STORAGE_KEY = "theme";

function resolveInitialIsDark(): boolean {
  if (typeof window === "undefined") return false;

  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "dark") return true;
  if (stored === "light") return false;

  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function ThemeToggle(): JSX.Element {
  const [isDark, setIsDark] = React.useState<boolean>(() => resolveInitialIsDark());

  React.useEffect(() => {
    document.documentElement.classList.toggle("dark", isDark);
  }, [isDark]);

  const toggleTheme = React.useCallback(() => {
    setIsDark((previous) => {
      const next = !previous;
      window.localStorage.setItem(THEME_STORAGE_KEY, next ? "dark" : "light");
      return next;
    });
  }, []);

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      onClick={toggleTheme}
      aria-label={isDark ? "라이트 모드로 전환" : "다크 모드로 전환"}
      aria-pressed={isDark}
    >
      {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </Button>
  );
}
