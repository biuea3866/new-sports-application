/**
 * Infrastructure Layer
 *
 * 책임: Domain interface 구현체 (RepositoryImpl, GatewayImpl, DomainEventPublisherImpl).
 *       기술 어댑터 (JPA, MongoDB, Redis, Kafka, 외부 API).
 *
 * 의존: domain layer만 허용.
 * 금지: presentation / application 직접 import.
 */
package com.sportsapp.infrastructure
