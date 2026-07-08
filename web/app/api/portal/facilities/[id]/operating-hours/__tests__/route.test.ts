/**
 * BFF Route Handler 테스트 — /api/portal/facilities/[id]/operating-hours
 * PUT : 운영시간 등록/수정 → BE PUT /facilities/{facilityId}/operating-hours forward
 *   - happy path: 유효한 body → BE forward + 200 반환
 *   - openTime >= closeTime → 400 (BE 호출 없음)
 *   - capacity 0 이하 → 400 (BE 호출 없음)
 * BE 403(비소유자): forward
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Facility Operating Hours Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("유효한 운영시간이면 BE에 forward하고 200을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const beResponseBody = {
      id: "facility-001",
      operatingHours: [
        {
          dayOfWeek: "MONDAY",
          openTime: "06:00",
          closeTime: "22:00",
          breaks: [],
          slotDurationMinutes: 60,
          capacity: 10,
        },
      ],
      holidays: [],
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(beResponseBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { PUT } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/operating-hours",
      {
        method: "PUT",
        body: JSON.stringify({
          operatingHours: [
            {
              dayOfWeek: "MONDAY",
              openTime: "06:00",
              closeTime: "22:00",
              capacity: 10,
            },
          ],
        }),
        headers: { "Content-Type": "application/json" },
      }
    );
    const response = await PUT(request, { params: { id: "facility-001" } });

    expect(response.status).toBe(200);
    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url] = mockFetch.mock.calls[0] as [string];
    expect(url).toBe("http://localhost:8080/facilities/facility-001/operating-hours");
  });

  it("openTime이 closeTime보다 늦으면 400을 반환하고 BE를 호출하지 않는다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const { PUT } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/operating-hours",
      {
        method: "PUT",
        body: JSON.stringify({
          operatingHours: [
            {
              dayOfWeek: "MONDAY",
              openTime: "22:00",
              closeTime: "06:00",
              capacity: 10,
            },
          ],
        }),
        headers: { "Content-Type": "application/json" },
      }
    );
    const response = await PUT(request, { params: { id: "facility-001" } });

    expect(response.status).toBe(400);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("capacity가 0이면 400을 반환하고 BE를 호출하지 않는다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const { PUT } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/operating-hours",
      {
        method: "PUT",
        body: JSON.stringify({
          operatingHours: [
            {
              dayOfWeek: "MONDAY",
              openTime: "06:00",
              closeTime: "22:00",
              capacity: 0,
            },
          ],
        }),
        headers: { "Content-Type": "application/json" },
      }
    );
    const response = await PUT(request, { params: { id: "facility-001" } });

    expect(response.status).toBe(400);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("BE가 403을 반환하면 403 + 권한 안내 메시지를 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Forbidden", status: 403 }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { PUT } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/operating-hours",
      {
        method: "PUT",
        body: JSON.stringify({
          operatingHours: [
            {
              dayOfWeek: "MONDAY",
              openTime: "06:00",
              closeTime: "22:00",
              capacity: 10,
            },
          ],
        }),
        headers: { "Content-Type": "application/json" },
      }
    );
    const response = await PUT(request, { params: { id: "facility-001" } });

    expect(response.status).toBe(403);
    const body = (await response.json()) as { message: string };
    expect(body.message).toBe("해당 작업을 수행할 권한이 없습니다.");
  });
});
