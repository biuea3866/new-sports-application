package com.sportsapp.domain.common.security

/**
 * SecurityContext 에 담기는 인증 주체의 최상위 계약 (W1-06b).
 *
 * edge 의 인증 필터는 자격증명을 검증한 뒤 그 결과 주체를 SecurityContext 에 주입해야 하는데,
 * 주체의 구체 타입(`UserPrincipal` / MCP 토큰 주체)은 각자 다른 컨텍스트가 소유한다.
 * edge 가 그 구체 타입들을 알면 `edge → platform` 의존이 생기므로(W1-01a 가 확정한 모듈 그래프 위반),
 * edge 는 이 마커 타입만 알고 구체 주체 생성은 조립자(bootstrap 의 로컬 어댑터)가 담당한다.
 *
 * 마커에 멤버를 두지 않는다 — 주체별로 노출 필드가 다르고, 공통 상위 필드를 강제하면
 * 어느 한쪽에 의미 없는 값을 채워야 한다. 필드가 필요한 소비자는 자기 구체 타입으로 캐스팅한다
 * (`AuthorizationExpressions`·`McpToolAuditHelper` 가 오늘 이미 그 방식이다).
 */
interface AuthenticatedPrincipal
