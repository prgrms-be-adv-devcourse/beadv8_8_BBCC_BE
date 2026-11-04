#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로가 APP_ENV(dev|prod) 와 이미지 아홉(IMAGE · SEARCH_IMAGE · GATEWAY_IMAGE · CHILDPROFILE_IMAGE · RECOMMEND_IMAGE · SETTLEMENT_IMAGE · MEMBER_IMAGE · PAYMENT_IMAGE · PRODUCT_IMAGE)을 넘긴다
# 검색 서비스 이미지는 profile search, Gateway 이미지는 profile gateway, 자녀 프로필 이미지는 profile childprofile, 추천 이미지는 profile recommend, 정산 이미지는 profile settlement, 회원 이미지는 profile member, 결제 이미지는 profile payment, 상품 이미지는 profile product 가 켜진 환경(.env 의 COMPOSE_PROFILES)에서만 받고 띄운다. Elasticsearch 는 t3.medium k3s(deploy/search)에 있다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}" "${IMAGE:?IMAGE 가 필요합니다}"
: "${SEARCH_IMAGE:?SEARCH_IMAGE 가 필요합니다}" "${GATEWAY_IMAGE:?GATEWAY_IMAGE 가 필요합니다}"
: "${CHILDPROFILE_IMAGE:?CHILDPROFILE_IMAGE 가 필요합니다}" "${RECOMMEND_IMAGE:?RECOMMEND_IMAGE 가 필요합니다}"
: "${SETTLEMENT_IMAGE:?SETTLEMENT_IMAGE 가 필요합니다}" "${MEMBER_IMAGE:?MEMBER_IMAGE 가 필요합니다}" "${PAYMENT_IMAGE:?PAYMENT_IMAGE 가 필요합니다}"
: "${PRODUCT_IMAGE:?PRODUCT_IMAGE 가 필요합니다}"
[[ "$APP_ENV" == dev || "$APP_ENV" == prod ]] || { echo "Invalid APP_ENV"; exit 1; }
