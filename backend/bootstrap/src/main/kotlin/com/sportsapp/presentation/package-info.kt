/**
 * Presentation Layer
 *
 * 책임: 라우팅(Controller), 인증, Request→Command 변환, UseCase 호출,
 *       Kafka Consumer(EventWorker), Spring EventListener.
 *
 * 의존: application layer만 허용.
 * 금지: 비즈니스 로직, domain/infrastructure 직접 import.
 */
package com.sportsapp.presentation
