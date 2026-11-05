# Groq 무료 분석 시험

**dev 1회 수동 시험입니다. 정기 실행은 없습니다.** 기존 MCP 수동 분석은 그대로 사용합니다.

1. Groq Console에서 **Free 플랜**을 확인하고 API 키를 만듭니다. 카드 등록·Developer 업그레이드는 하지 않습니다
2. GitHub → Settings → Environments → dev → Environment secrets에 **GROQ_API_KEY**를 등록합니다. 키는 채팅·파일·로그에 남기지 않습니다
3. 코드가 develop 모니터링에 배포된 뒤 Actions → **ai-notes-groq-trial** → Run workflow → 무료 플랜 확인을 체크합니다
4. Grafana → Kidly → **Kidly AI 운영 분석** → dev. 최근 행을 펼쳐 **Groq/openai/gpt-oss-20b**의 관측·원인 후보·확인 순서·대응 제안을 봅니다

한 번의 실행은 API를 최대 한 번 호출합니다. 실패 포함 1시간 안의 새 시험은 차단합니다.
개발 환경의 Secret 접근 정책을 확인하고 검토된 develop workflow로 실행합니다. 임의 브랜치의 workflow에 키를 제공하지 않습니다.
3,000토큰에는 모델의 추론 토큰도 포함됩니다. 출력이 잘리면 게시하지 않고 실패하며 상한을 자동으로 올리지 않습니다.
핵심1개 항목만 짧게 요청하고 확인 항목1개와 실제 근거 ID1개를 필수 값으로 받습니다. 어댑터가 공통 배열 형식으로 변환합니다. 원본 문맥의 제약·근거는 기존 게시기가 별도로 표시합니다.

| 오류 | 확인할 것 |
| --- | --- |
| authentication_failed | Groq 키와 계정 확인. 키를 로그에 출력하지 않음 |
| access_denied | 요청 식별자·접근 정책·서버 egress 확인. 키 오류로 단정하지 않음 |
| insufficient_evidence | 발급 지표가 없음. 모델을 호출하지 않음 |
| response_incomplete / response_refused | 모델 미완료 또는 거부. 게시하지 않음 |
| response_text_invalid / response_evidence_missing / response_timestamp_invalid | 비어 있거나 잘못된 필수 값·시각 확인 |
| response_truncated | 추론 포함 출력 상한에 걸림. 잘린 결과는 게시하지 않음 |
| response_evidence_invalid / response_text_unsafe | 근거 또는 본문 검증 거부. 검증을 끄지 않음 |
| response_model_mismatch / response_shape_invalid / response_json_invalid / response_contract_invalid / response_fields_invalid / response_array_invalid | 제공자 응답 계약 확인. 다른 모델로 자동 전환하지 않음 |

무료 한도·오류·잘못된 결과·timeout은 기존 메모를 유지하고 실패합니다. 유료/다른 모델 전환과 자동 재호출은 없습니다.
Loki 전송 실패는 기존 outbox 운영 절차로 저장본만 복구하며 AI를 다시 부르지 않습니다.

키는 GitHub Secret에서 암호화된 SSH stdin으로만 전달하며 서버 파일·명령 인자·출력에는 넣지 않습니다.
수신 프로세스는 100초/CPU10초/가상 주소공간384MiB, 외부 HTTP는60초·응답64KiB, 출력은3,000토큰 상한입니다.
Groq API 주소와 모델은 고정이며 프록시/리다이렉트는 사용하지 않습니다. 새 패키지·이미지·데몬은 없습니다.

**무료 여부는 계정 플랜에 달려 있습니다.** 호출 API만으로 Free 플랜을 증명하지 못하므로 실행자가 콘솔에서 확인해야 합니다.
유료 계정의 키를 넣고 확인을 체크하면 비용이 발생할 수 있습니다. 무료 한도는 계정/모델에 따라 바뀝니다.
Groq는 지표 집계 숫자·허용된 서비스명·누락 제약만 받습니다. 로그 원문·개인정보·이전 대화는 보내지 않습니다.
출력 형식 검증이 추론의 정확성을 보장하지 않습니다. 원인은 가설이며 조치는 사람이 판단합니다.

[Groq 무료 한도](https://console.groq.com/docs/rate-limits) · [데이터 정책](https://console.groq.com/docs/your-data) · [JSON 출력](https://console.groq.com/docs/structured-outputs)
