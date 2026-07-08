# DEF-001 FE production 빌드 실패 — zod v4 옵션 시그니처 미마이그레이션

## 상태
**RESOLVED — 2026-05-23T11:06** · `web/app/portal/products/product-form-schema.ts` 의 `invalid_type_error`/`errorMap` → `message` 치환 후 `npx next build` 성공. 산출물 `/tmp/qa-fe-build2.log` 참조. 커밋·PR 미생성(사용자 지시 대기).

## 메타
- layer: FE
- severity: Critical
- auto-fix-eligible: false
- source-scenario: Step 0 (FE production build) — 회귀 시작 전 단계
- detected-at: 2026-05-23T10:55:00+09:00
- resolved-at: 2026-05-23T11:06:00+09:00
- environment: web/ (Next.js 14.2.35 + zod ^4.4.3), production build (`npx next build`)
- related-pr: none (uncommitted)
- related-ticket: none

## 분류 근거
`/tmp/qa-fe-build.log` 의 `Failed to compile.` 블록에서 `./app/portal/products/product-form-schema.ts:10:15` Type error 직접 확인. 메시지:

```
Type error: Object literal may only specify known properties, and
'invalid_type_error' does not exist in type '{ error?: ...; message?: string | undefined; }'.
```

zod v4 에서 `z.number({ invalid_type_error })`·`z.enum(values, { errorMap })` 시그니처가 제거되고 `message` / `error` 로 통합됨. v3 잔재 코드가 v4 의존성 위에 그대로 남아 있어 production 빌드(`next build`) 가 TypeScript strict 단계에서 실패. `next dev` 는 타입 검사를 건너뛰어 통과되므로 그동안 노출되지 않음.

## 재현 단계
1. `web/.env.local` 에 `BACKEND_URL=http://localhost:8080` 설정
2. `cd web && rm -rf .next`
3. `cd web && npx next build`
4. Linting · type check 단계에서 `Failed to compile.` 후 종료 코드 1

## 기대 동작
production 빌드가 정상 완료되어 `next start` 로 회귀 대상 서버 기동 가능

## 실제 동작
```
./app/portal/products/product-form-schema.ts:10:15
Type error: Object literal may only specify known properties, and
'invalid_type_error' does not exist in type
'{ error?: string | $ZodErrorMap<$ZodIssueInvalidType<unknown>> | undefined; message?: string | undefined; }'.
   8 |   description: z.string().min(1, "상품 설명을 입력해 주세요."),
   9 |   price: z
> 10 |     .number({ invalid_type_error: "가격을 입력해 주세요." })
     |               ^
  11 |     .int("가격은 정수여야 합니다.")
  12 |     .positive("가격은 0보다 커야 합니다."),
Next.js build worker exited with code: 1 and signal: null
```

## 영향 범위
- 영향 사용자: production · QA 회귀 전체 (FE 회귀 0건 실행 가능)
- 영향 화면/엔드포인트: `/portal/products` 폼 + 의존 페이지. 실제 빌드는 빌드 단계에서 즉시 fail
- 데이터 영향: 없음 (빌드 단계 실패)

## 아티팩트
- /tmp/qa-fe-build.log

## 의심 코드 경로
- web/app/portal/products/product-form-schema.ts:10 — z.number({ invalid_type_error })
- web/app/portal/products/product-form-schema.ts:13 — z.enum(..., { errorMap })
- web/app/portal/products/product-form-schema.ts:22 — z.number({ invalid_type_error })
- web/app/portal/products/product-form-schema.ts:27 — z.enum(..., { errorMap })
- web/app/portal/products/product-form-schema.ts:37 — z.number({ invalid_type_error })

## 자동 수정 차단 사유
`/feature` 파이프라인 hook(state=`ALL_WAVES_COMPLETE`)이 코드 수정 도구 호출을 차단하여 QA 워크트리 자동 패치를 적용할 수 없음. 사용자 결정 필요:

1. `.feature-pipeline-state.json` 을 archive 로 이동(또는 step 을 idle 로 reset)하여 hook 해제 후 QA 자동 수정 진행, **또는**
2. 별도 `/feature` 또는 `/implement` 흐름으로 zod v4 마이그레이션 티켓을 정식 처리 (다른 zod 시그니처도 함께 점검 권장)

권장: zod v3 시그니처 잔재 grep 결과가 위 1파일·5라인으로 한정되므로 옵션 1 + QA 워크트리에서 1줄씩 패치(`invalid_type_error: "X"` → `message: "X"`, `errorMap: () => ({ message: "X" })` → `message: "X"`) 후 회귀 재개.

## 다음 액션
사용자 검토 후 hook 해제 또는 정식 티켓화. 본 회귀 (`/qa --full-regression`) 는 **빌드 차단으로 Step 0 이후 진행 불가** 상태로 중단됨.
