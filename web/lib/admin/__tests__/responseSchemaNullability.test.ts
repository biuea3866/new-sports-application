/**
 * 어드민 **응답 스키마** nullability 불변식 가드.
 *
 * 배경: BE `McpObjectMapperConfig`(@Primary)가 `JsonInclude.NON_NULL`로 직렬화하는 동안에는
 * nullable 필드가 null이면 응답에서 **키 자체가 생략**된다(→ FE는 `undefined`를 본다).
 * 매퍼가 Spring Boot 기본 동작으로 복원되면 같은 필드가 **`null`로 명시**돼 온다.
 *
 * 즉 계약이 양방향으로 바뀔 수 있으므로, 응답 스키마의 모든 필드는
 *
 *      null 허용  ⟺  undefined 허용
 *
 * 이어야 한다. 한쪽만 허용하면(`.nullable()` 또는 `.optional()`) 계약이 바뀌는 순간 화면이 죽는다.
 * - `.nullable()`  → 지금(NON_NULL) 터진다 (05 감사 로그 장애의 실제 원인)
 * - `.optional()`  → BE 매퍼 복원 후 터진다
 * - `.nullish()`   → 양쪽 모두 안전 ✅
 * - 필수 필드      → 양쪽 모두 거부 ✅ (누락은 진짜 계약 위반이므로 걸러야 맞다)
 *
 * 텍스트 grep이 아니라 **스키마를 실제로 파싱시켜** 판정하므로,
 * 새 응답 스키마가 추가돼도 아래 RESPONSE_SCHEMAS에 등록만 하면 중첩 필드까지 자동 검사된다.
 *
 * 주의: **요청(입력) 스키마는 대상이 아니다.** FE가 BE로 보내는 값은 우리가 형태를 정하므로
 * `.nullable()`(명시적 null 전송)이 올바른 선택이다 — 예: `IssueMcpTokenInputSchema.expiresAt`.
 */
import { describe, it, expect } from "vitest";
import { z } from "zod";

import {
  FeatureFlagSnapshotSchema,
  FeatureFlagResponseSchema,
  FeatureFlagAuditLogResponseSchema,
  FeatureFlagAuditLogPageSchema,
} from "../feature-flags/schemas";

/** 검사 대상 — BE 응답을 파싱하는 스키마만 등록한다. */
const RESPONSE_SCHEMAS: ReadonlyArray<{ name: string; schema: z.ZodType }> = [
  { name: "FeatureFlagSnapshotSchema", schema: FeatureFlagSnapshotSchema },
  { name: "FeatureFlagResponseSchema", schema: FeatureFlagResponseSchema },
  { name: "FeatureFlagAuditLogResponseSchema", schema: FeatureFlagAuditLogResponseSchema },
  { name: "FeatureFlagAuditLogPageSchema", schema: FeatureFlagAuditLogPageSchema },
];

interface FieldViolation {
  path: string;
  acceptsNull: boolean;
  acceptsUndefined: boolean;
}

/** zod 내부 정의 접근용 최소 형태 — `any` 없이 좁혀 쓴다. */
interface ZodInternalDef {
  type?: string;
  shape?: Record<string, z.ZodType>;
  innerType?: z.ZodType;
  element?: z.ZodType;
  options?: z.ZodType[];
}

function definitionOf(schema: z.ZodType): ZodInternalDef {
  return (schema as unknown as { def: ZodInternalDef }).def;
}

/** 래퍼(optional/nullable/default 등)를 벗겨 안쪽 스키마를 얻는다. */
function unwrap(schema: z.ZodType): z.ZodType {
  let current = schema;
  for (let depth = 0; depth < 10; depth += 1) {
    const inner = definitionOf(current).innerType;
    if (inner === undefined) return current;
    current = inner;
  }
  return current;
}

/**
 * 스키마를 재귀 순회하며 "null과 undefined 허용 여부가 어긋나는" 필드를 모은다.
 * 판정은 실제 `safeParse` 결과로 한다 — 내부 표현이 아니라 동작을 검증한다.
 */
function collectViolations(
  schema: z.ZodType,
  pathPrefix: string,
  visited: Set<z.ZodType> = new Set()
): FieldViolation[] {
  const unwrapped = unwrap(schema);
  if (visited.has(unwrapped)) return [];
  visited.add(unwrapped);

  const definition = definitionOf(unwrapped);

  if (definition.shape !== undefined) {
    return Object.entries(definition.shape).flatMap(([key, fieldSchema]) => {
      const path = pathPrefix === "" ? key : `${pathPrefix}.${key}`;
      const acceptsNull = fieldSchema.safeParse(null).success;
      const acceptsUndefined = fieldSchema.safeParse(undefined).success;

      const violation: FieldViolation[] =
        acceptsNull === acceptsUndefined ? [] : [{ path, acceptsNull, acceptsUndefined }];

      return [...violation, ...collectViolations(fieldSchema, path, visited)];
    });
  }

  if (definition.element !== undefined) {
    return collectViolations(definition.element, `${pathPrefix}[]`, visited);
  }

  if (definition.options !== undefined) {
    return definition.options.flatMap((option, index) =>
      collectViolations(option, `${pathPrefix}|${index}`, visited)
    );
  }

  return [];
}

describe("응답 스키마는 null과 undefined를 동일하게 취급한다", () => {
  it.each(RESPONSE_SCHEMAS)("$name 의 모든 필드가 불변식을 만족한다", ({ schema }) => {
    const violations = collectViolations(schema, "");

    expect(violations).toEqual([]);
  });

  // 가드 자체가 동작하는지 확인 — 규칙을 어긴 스키마를 실제로 잡아내야 한다.
  it("`.nullable()` 필드(현재 BE에서 터짐)를 위반으로 잡아낸다", () => {
    const brokenSchema = z.object({ description: z.string().nullable() });

    expect(collectViolations(brokenSchema, "")).toEqual([
      { path: "description", acceptsNull: true, acceptsUndefined: false },
    ]);
  });

  it("`.optional()` 필드(BE 매퍼 복원 후 터짐)를 위반으로 잡아낸다", () => {
    const brokenSchema = z.object({ description: z.string().optional() });

    expect(collectViolations(brokenSchema, "")).toEqual([
      { path: "description", acceptsNull: false, acceptsUndefined: true },
    ]);
  });

  it("중첩 객체·배열 안쪽 필드까지 검사한다", () => {
    const brokenSchema = z.object({
      content: z.array(z.object({ before: z.object({ key: z.string() }).nullable() })),
    });

    expect(collectViolations(brokenSchema, "")).toEqual([
      { path: "content[].before", acceptsNull: true, acceptsUndefined: false },
    ]);
  });

  it("`.nullish()`와 필수 필드는 위반이 아니다", () => {
    const safeSchema = z.object({ description: z.string().nullish(), key: z.string() });

    expect(collectViolations(safeSchema, "")).toEqual([]);
  });
});
