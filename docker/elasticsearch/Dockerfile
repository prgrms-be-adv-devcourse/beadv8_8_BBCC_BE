# 검색(#526)이 쓰는 Elasticsearch. 공식 이미지에 한글 형태소 분석기(analysis-nori)를 더한다
# 로컬 compose(docker-compose.yml) · 검색 서비스 통합 테스트(ElasticsearchTestcontainersConfiguration) · 서버가 같은 파일로 만든다
# 버전은 클라이언트(elasticsearch-java, Boot BOM 9.4.x)와 같은 마이너로 맞춘다
FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.6

RUN bin/elasticsearch-plugin install --batch analysis-nori
