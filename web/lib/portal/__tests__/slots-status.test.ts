/**
 * closeSlot / openSlot 액션 테스트 (lib/portal/slots.ts 확장).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { closeSlot, openSlot } from "../slots";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const closedSlot = {
  id: 1,
  facilityId: "facility-001",
  date: "2026-07-12T00:00:00Z",
  timeRange: "14:00-15:00",
  capacity: 8,
  ownerId: 1,
  status: "CLOSED",
  programId: null,
};

const openSlotBody = {
  ...closedSlot,
  id: 2,
  status: "OPEN",
};

describe("closeSlot", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("마감 성공 시 status가 CLOSED인 슬롯을 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(closedSlot));

    const result = await closeSlot("facility-001", 1);

    expect(result.status).toBe("CLOSED");
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/portal/facilities/facility-001/slots/1/close");
    expect(init.method).toBe("PATCH");
  });

  it("이미 CLOSED인 슬롯을 다시 마감하면(409) 에러를 throw한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "이미 존재하거나 충돌이 발생했습니다." }, 409));

    await expect(closeSlot("facility-001", 1)).rejects.toThrow(
      "이미 존재하거나 충돌이 발생했습니다."
    );
  });
});

describe("openSlot", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("오픈 성공 시 status가 OPEN인 슬롯을 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(openSlotBody));

    const result = await openSlot("facility-001", 2);

    expect(result.status).toBe("OPEN");
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/portal/facilities/facility-001/slots/2/open");
    expect(init.method).toBe("PATCH");
  });

  it("권한이 없으면(403) 에러를 throw한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "해당 작업을 수행할 권한이 없습니다." }, 403));

    await expect(openSlot("facility-001", 2)).rejects.toThrow(
      "해당 작업을 수행할 권한이 없습니다."
    );
  });
});
