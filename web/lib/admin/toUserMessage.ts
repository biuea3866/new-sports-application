/**
 * 어드민 공통 오류 → 사용자 메시지 변환.
 *
 * 구현은 `lib/errors/userMessage.ts`가 포털과 공유한다 — 스키마 검증 실패 원문이 화면에 그대로
 * 노출되는 같은 결함이 어드민(05-피처플래그-감사로그)과 포털(14-매출-결제-내역) 양쪽에서
 * 발생해, 한쪽만 고쳐진 채 남지 않도록 처리를 한 곳으로 모았다.
 */
import { createUserMessageMapper } from "../errors/userMessage";

export const toUserMessage = createUserMessageMapper("admin");
