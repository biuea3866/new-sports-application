# MCP WAF / Rate Limit 설정
#
# default config (DevOps Open Issue #B 결정 전):
#   nginx limit_req 를 Terraform null_resource + remote-exec 로 관리.
#   DevOps 결정 후 아래 중 하나로 전환:
#     - Cloudflare → cloudflare_ruleset 리소스
#     - AWS WAF    → aws_wafv2_web_acl 리소스
#
# 현재: nginx upstream 서버에 rate limit zone 을 선언.
# nginx limit_req 설정은 infra/nginx/mcp.conf 에 이미 포함됨.
# 이 파일은 변수·문서화·향후 전환 가이드 역할을 한다.

terraform {
  required_version = ">= 1.5"
}

# ------------------------------------------------------------------
# 변수
# ------------------------------------------------------------------

variable "mcp_domain" {
  description = "MCP API 노출 도메인 (placeholder — DevOps 결정 후 변경)"
  type        = string
  default     = "mcp-api.sportsapp.com"
}

variable "rate_limit_ip_rpm" {
  description = "IP 기반 rate limit (req/min, anonymous 또는 미인증 요청)"
  type        = number
  default     = 100
}

variable "rate_limit_token_rpm" {
  description = "토큰 기반 rate limit (req/min, Authorization 헤더 보유 — 인증된 MCP 클라이언트)"
  type        = number
  default     = 600
}

variable "rate_limit_admin_rpm" {
  description = "Admin API rate limit (req/min, IP 기반)"
  type        = number
  default     = 60
}

variable "nginx_server_host" {
  description = "nginx 서버 호스트 (원격 적용 시 사용)"
  type        = string
  default     = "localhost"
}

# ------------------------------------------------------------------
# outputs — 실제 nginx.conf 에 적용할 값 문서화
# ------------------------------------------------------------------

output "rate_limit_summary" {
  description = "적용된 rate limit 요약"
  value = {
    ip_based    = "${var.rate_limit_ip_rpm} req/min (zone: mcp_ip, 10MB)"
    token_based = "${var.rate_limit_token_rpm} req/min (zone: mcp_token, 20MB)"
    admin_api   = "${var.rate_limit_admin_rpm} req/min (zone: mcp_ip — admin 경로 burst=30)"
    domain      = var.mcp_domain
  }
}

# ------------------------------------------------------------------
# (향후 전환 가이드)
# Cloudflare WAF 전환 시:
#
# provider "cloudflare" {
#   api_token = var.cloudflare_api_token
# }
#
# resource "cloudflare_ruleset" "mcp_rate_limit" {
#   zone_id = var.cloudflare_zone_id
#   name    = "MCP Rate Limit"
#   kind    = "zone"
#   phase   = "http_ratelimit"
#
#   rules {
#     action = "block"
#     action_parameters { response { status_code = 429 } }
#     expression  = "(http.request.uri.path contains \"/mcp/\")"
#     description = "MCP IP rate limit ${var.rate_limit_ip_rpm} rpm"
#     ratelimit {
#       characteristics     = ["cf.colo.id", "ip.src"]
#       period              = 60
#       requests_per_period = var.rate_limit_ip_rpm
#       mitigation_timeout  = 60
#     }
#   }
# }
#
# AWS WAF 전환 시:
#
# resource "aws_wafv2_web_acl" "mcp" {
#   name  = "mcp-rate-limit"
#   scope = "REGIONAL"
#   ...
#   rule {
#     name     = "mcp-ip-rate-limit"
#     priority = 1
#     action { block {} }
#     statement {
#       rate_based_statement {
#         limit              = var.rate_limit_ip_rpm
#         aggregate_key_type = "IP"
#       }
#     }
#   }
# }
# ------------------------------------------------------------------
