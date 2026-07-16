#!/usr/bin/env bash
# dev 데모용 전체 시드 (MySQL + MongoDB). 재실행 가능(idempotent).
# 사용: scripts/seed-dev.sh
# 전제: dev 스택(sports-dev-mysql-1, sports-dev-mongodb-1)이 떠 있어야 한다.
# prod 에는 절대 실행하지 않는다.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_C="${MYSQL_CONTAINER:-sports-dev-mysql-1}"
MONGO_C="${MONGO_CONTAINER:-sports-dev-mongodb-1}"
DB="${DB_NAME:-sports}"

echo "▶ MySQL 시드 ($MYSQL_C/$DB)…"
# --default-character-set=utf8mb4 필수 (없으면 한글이 latin1 로 깨진다)
docker exec -i "$MYSQL_C" mysql --default-character-set=utf8mb4 -uroot -proot "$DB" \
  < "$DIR/seed-dev.sql" | grep -v '^Warning' || true

echo "▶ MongoDB 시설 시드 ($MONGO_C/$DB)…"
docker exec -i "$MONGO_C" mongosh "$DB" --quiet < "$DIR/seed-dev-facilities.js"

echo "✔ 시드 완료. 데모 로그인 계정이 없으면 앱에서 회원가입 후 확인하세요."
echo "  (커뮤니티 멤버·채팅방 참여자는 e2e@example.com 계정 기준으로 배선됩니다.)"
