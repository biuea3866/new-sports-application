#!/usr/bin/env python3
"""Grafana Alerting webhook contact point 부트스트랩 (Provisioning 파일 우회, Alertmanager 설정 API 경유).

# 배경 (실측, 후속 티켓 — 지능형 장애 알림 배포 게이트 선결 항목)
Grafana 11.1.0·11.4.0 두 버전 모두, 파일 프로비저닝(`provisioning/alerting/contact-points.yaml`)으로
등록한 webhook contact point는 secureSettings(authorization_credentials)가 저장(REDACTED 확인)은
되지만, 실제 알림 발송 시 Authorization 헤더가 완전히 누락된다 — Bearer·Basic 두 인증 스킴 모두
동일 증상(패킷 캡처로 재현: 헤더 목록에 Host/User-Agent/Content-Length/Content-Type/Accept-Encoding만
존재, Authorization 부재). 반면 이 스크립트처럼 Alertmanager 설정 API
(`POST /api/alertmanager/grafana/config/api/v1/alerts`)로 등록한 동일 설정은 두 버전 모두 정상
발송된다(재현: 최초 등록 시점 + Grafana 컨테이너 재기동 후 재평가 시점 모두 Authorization 헤더 확인).

이 스크립트는 Grafana가 기동한 뒤(healthcheck 통과) 이 API로 backend-webhook 리시버를 등록하고
루트 라우트의 기본 수신자로 지정한다 — `provisioning/alerting/latency-rules.yaml`의 두 규칙은
더 이상 `notification_settings.receiver`를 파일에 명시하지 않고(파일 프로비저닝 시점에는 아직
이 리시버가 존재하지 않아 검증에 실패하므로) 기본 라우트를 그대로 상속받는다.

# 멱등성
매 컨테이너 기동마다(재기동 포함) 실행되며, 기존 backend-webhook 리시버를 제거 후 최신 값으로
재삽입한다 — 몇 번을 실행해도 최종 상태는 같다. Alertmanager 설정은 Grafana 내부 DB에 저장되어
`grafana-data` 볼륨과 함께 컨테이너 재기동 간 유지된다(파일 프로비저닝이 이 리시버를 정의하지
않으므로 재시작 시 되돌아가지 않는다).

# 롤백
이 스크립트를 compose에서 제거하고 Grafana 컨테이너를 재기동하면 root route가
`grafana-default-email`로 유지되어(파일 프로비저닝이 별도로 덮어쓰지 않는 한) 알림이 발송되지
않는 상태로 되돌아간다 — 알림 파이프라인만 비활성화될 뿐 다른 관측 스택(Prometheus·Tempo·Loki)에는
영향이 없다.
"""
import base64
import json
import os
import time
import urllib.error
import urllib.request

GRAFANA_URL = os.environ.get("GRAFANA_URL", "http://grafana:3000")
GRAFANA_ADMIN_USER = os.environ["GRAFANA_ADMIN_USER"]
GRAFANA_ADMIN_PASSWORD = os.environ["GRAFANA_ADMIN_PASSWORD"]
ALERT_WEBHOOK_TOKEN = os.environ["ALERT_WEBHOOK_TOKEN"]
BACKEND_WEBHOOK_URL = os.environ.get("BACKEND_WEBHOOK_URL", "http://backend:8080/internal/alerts/grafana")
RECEIVER_NAME = "backend-webhook"
HEALTH_TIMEOUT_SECONDS = 120
HEALTH_POLL_INTERVAL_SECONDS = 2


def wait_for_grafana_health() -> None:
    deadline = time.time() + HEALTH_TIMEOUT_SECONDS
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"{GRAFANA_URL}/api/health", timeout=5) as response:
                if response.status == 200:
                    return
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(HEALTH_POLL_INTERVAL_SECONDS)
    raise TimeoutError(f"Grafana health check timed out after {HEALTH_TIMEOUT_SECONDS}s")


def call_grafana_api(method: str, path: str, body: dict | None = None) -> dict | None:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(f"{GRAFANA_URL}{path}", data=data, method=method)
    request.add_header("Content-Type", "application/json")
    credentials = f"{GRAFANA_ADMIN_USER}:{GRAFANA_ADMIN_PASSWORD}".encode("utf-8")
    request.add_header("Authorization", "Basic " + base64.b64encode(credentials).decode("ascii"))
    with urllib.request.urlopen(request, timeout=10) as response:
        raw_body = response.read()
        return json.loads(raw_body) if raw_body else None


def build_backend_webhook_receiver() -> dict:
    return {
        "name": RECEIVER_NAME,
        "grafana_managed_receiver_configs": [
            {
                "name": RECEIVER_NAME,
                "type": "webhook",
                "settings": {
                    "url": BACKEND_WEBHOOK_URL,
                    "httpMethod": "POST",
                    "authorization_scheme": "Bearer",
                    "authorization_credentials": ALERT_WEBHOOK_TOKEN,
                },
                "disableResolveMessage": False,
            }
        ],
    }


def register_backend_webhook_as_default_receiver() -> None:
    current_config = call_grafana_api("GET", "/api/alertmanager/grafana/config/api/v1/alerts")
    alertmanager_config = current_config["alertmanager_config"]

    other_receivers = [r for r in alertmanager_config["receivers"] if r["name"] != RECEIVER_NAME]
    alertmanager_config["receivers"] = other_receivers + [build_backend_webhook_receiver()]
    alertmanager_config["route"]["receiver"] = RECEIVER_NAME

    call_grafana_api(
        "POST",
        "/api/alertmanager/grafana/config/api/v1/alerts",
        {
            "template_files": current_config.get("template_files") or {},
            "alertmanager_config": alertmanager_config,
        },
    )


def main() -> None:
    wait_for_grafana_health()
    register_backend_webhook_as_default_receiver()
    print(f"registered '{RECEIVER_NAME}' receiver and set it as the default alertmanager route receiver")


if __name__ == "__main__":
    main()
