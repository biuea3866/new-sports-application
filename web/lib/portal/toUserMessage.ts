/**
 * 포털 공통 오류 → 사용자 메시지 변환.
 *
 * 구현은 `lib/errors/userMessage.ts`가 어드민과 공유한다 — 화면군마다 스키마 실패 처리를
 * 따로 짜면 한쪽만 고쳐진 채 남는다(매출 화면에 Zod issue 원문이 노출된 결함).
 */
import { createUserMessageMapper } from "../errors/userMessage";

export const toUserMessage = createUserMessageMapper("portal");
