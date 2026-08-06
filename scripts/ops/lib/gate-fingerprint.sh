#!/usr/bin/env bash
# 게이트 지문(fingerprint) 계산 — 게이트 실행기와 검증 훅이 **같은 정의**를 써야 하므로 공용 lib.
#
# 지문은 "게이트가 검사한 코드 상태"를 식별한다. HEAD 커밋만으로는 부족하다 —
# 게이트 실행 후 파일을 고치고 push 하면 검사받지 않은 코드가 나간다. 그래서
# 커밋 + 추적 파일의 미커밋 변경 + 미추적 파일 내용을 모두 지문에 넣는다.
#
# build/ 는 .gitignore 대상이라 리포트 자체를 쓰는 행위가 지문을 바꾸지 않는다.

# gate_fingerprint <repo_root>
gate_fingerprint() {
  local repo_root="$1"
  {
    git -C "$repo_root" rev-parse HEAD
    git -C "$repo_root" diff HEAD --binary
    git -C "$repo_root" ls-files -o --exclude-standard -z \
      | LC_ALL=C sort -z \
      | while IFS= read -r -d '' f; do
          printf '%s\n' "$f"
          shasum -a 256 "$repo_root/$f" 2>/dev/null | awk '{print $1}'
        done
  } 2>/dev/null | shasum -a 256 | awk '{print $1}'
}

# 이 변경 집합이 게이트 대상인지 판정한다 (Kotlin·Gradle·정적분석 설정이 걸리면 대상).
# 문서·FE·compose 만 바뀐 push 는 게이트를 요구하지 않는다 — 게이트가 검사하는 대상이 아니다.
#
# gate_required <repo_root> <base_ref>
gate_required() {
  local repo_root="$1" base_ref="${2:-origin/main}"
  local changed
  changed="$(
    {
      git -C "$repo_root" diff --name-only "$base_ref"...HEAD 2>/dev/null || true
      git -C "$repo_root" diff --name-only HEAD 2>/dev/null || true
      git -C "$repo_root" ls-files -o --exclude-standard 2>/dev/null || true
    } | LC_ALL=C sort -u
  )"
  printf '%s\n' "$changed" | grep -qE '^backend/.*\.(kt|kts)$|^backend/config/detekt/|^backend/detekt-baseline\.xml$|^backend/gradle\.properties$|^backend/settings\.gradle\.kts$'
}
