#!/bin/bash

# Clojure 프로그램 실행을 위한 스크립트

# 스크립트 사용법 출력 함수
usage() {
    echo "사용법: $0 [-f <파일 경로>] [-v] [-h]"
    echo "  -f <파일 경로>  : 이메일 데이터가 포함된 EDN 파일 경로"
    echo "  -v              : 상세 모드 활성화"
    echo "  -h              : 도움말 표시"
}

# 명령줄 인자가 없으면 사용법 출력
if [ $# -eq 0 ]; then
    usage
    exit 1
fi

# Clojure 프로그램 실행
clj -M -m send.core "$@"
