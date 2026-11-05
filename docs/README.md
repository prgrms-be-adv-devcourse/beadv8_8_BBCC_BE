# docs

팀이 함께 지키는 문서를 둔다. 누구나 PR로 고치고, 누가 언제 무엇을 바꿨는지는 git 이력으로 본다.

| 문서 | 위치 | 고치는 때 |
|---|---|---|
| Kafka 이벤트 규칙 · 토픽 표 | [kafka/events.md](kafka/events.md) | 이벤트를 만들거나 바꾸는 PR에서 같은 PR 안에 받는 곳 칸까지 고친다 |
| API 목록 | [api/README.md](api/README.md) | API를 추가하거나 바꾸는 PR에서 같은 PR 안에 고친다. 요청 · 응답은 Swagger에서 본다 |
| 기획서 | [planning/proposal.md](planning/proposal.md) | 기획이 바뀔 때. 리뷰어로 팀원 전체를 지정한다 |

- 파일 이름에 날짜나 판 번호를 붙이지 않는다. 이전 내용은 `git log -p <파일>`로 본다.
- 문서만 고칠 때는 `docs:` 커밋으로 올린다.
- 그림은 문서 옆 `images/` 폴더에 둔다.
