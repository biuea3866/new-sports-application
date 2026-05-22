"use client";

/**
 * 평문 토큰 1회 노출 모달.
 * 닫힌 후에는 다시 볼 수 없음을 사용자에게 명확히 안내한다.
 * 토큰은 메모리에만 보관하며 localStorage/cookie에 저장하지 않는다.
 */

import * as Dialog from "@radix-ui/react-dialog";

interface PlainTokenModalProps {
  open: boolean;
  tokenName: string;
  plainToken: string;
  onClose: () => void;
}

export function PlainTokenModal({
  open,
  tokenName,
  plainToken,
  onClose,
}: PlainTokenModalProps): JSX.Element {
  function handleCopy(): void {
    void navigator.clipboard.writeText(plainToken);
  }

  return (
    <Dialog.Root open={open} onOpenChange={(isOpen) => { if (!isOpen) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/50" />
        <Dialog.Content
          className="fixed left-1/2 top-1/2 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg bg-white p-6 shadow-lg"
          aria-describedby="plain-token-desc"
        >
          <Dialog.Title className="text-lg font-semibold text-gray-900">
            토큰 발급 완료 — {tokenName}
          </Dialog.Title>
          <p id="plain-token-desc" className="mt-2 text-sm text-amber-700 bg-amber-50 rounded p-2">
            이 화면을 닫으면 토큰을 다시 확인할 수 없습니다. 지금 바로 복사해 두세요.
          </p>
          <div className="mt-4">
            <label htmlFor="plain-token-value" className="block text-xs font-medium text-gray-500">
              발급된 토큰
            </label>
            <textarea
              id="plain-token-value"
              readOnly
              value={plainToken}
              rows={3}
              className="mt-1 w-full rounded border border-gray-300 bg-gray-50 p-2 font-mono text-xs text-gray-900 focus:outline-none"
              aria-label="발급된 MCP 토큰 (읽기 전용)"
            />
          </div>
          <div className="mt-4 flex justify-end gap-2">
            <button
              type="button"
              onClick={handleCopy}
              className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label="토큰 클립보드에 복사"
            >
              복사
            </button>
            <Dialog.Close asChild>
              <button
                type="button"
                className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400"
                aria-label="토큰 모달 닫기"
              >
                닫기
              </button>
            </Dialog.Close>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
