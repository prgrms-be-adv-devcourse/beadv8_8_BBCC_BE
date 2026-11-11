package com.bbcc.kidly.boundedcontext.search.out;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 검색어 단어를 같은 뜻의 말로 넓히는 동의어 사전이다(#1025). 검색할 때만 쓰고 색인은 바꾸지 않는다.
 * Elasticsearch의 synonym 분석기를 쓰면 색인 설정이 바뀌어 새 색인을 만들고 다시 채워야 하므로, 사전으로 넓힌 말을 쿼리에 더한다.
 * 사전 줄 형식:
 * <ul>
 *   <li>{@code 바지, 팬츠}: 양방향. 어느 말로 찾아도 모두 찾는다</li>
 *   <li>{@code 수영복 => 래시가드, 수영 모자}: 한 방향. 왼쪽 말로 찾으면 오른쪽 말도 찾는다(오른쪽 말로 찾을 때는 넓히지 않는다)</li>
 *   <li>{@code #}로 시작하는 줄과 빈 줄은 건너뛴다</li>
 * </ul>
 * 찾는 말은 공백 없는 한 단어여야 한다. 검색어를 공백으로 나눈 단어마다 사전을 찾기 때문이다.
 * 양방향 줄은 모든 말이 찾는 말이 되므로 모두 한 단어여야 하고, 한 방향 줄은 왼쪽만 한 단어면 된다. 어기면 기동에 실패한다.
 * 한 방향 줄의 오른쪽(넓힌 말)은 여러 단어여도 된다(상품명에서 형태소를 모두 맞춰 찾는다). 영문은 대소문자를 가리지 않는다.
 */
public final class SearchSynonyms {

    static final String DEFAULT_PATH = "search/synonyms.txt";

    private static final String ONE_WAY = "=>";

    private final Map<String, Set<String>> expansions;

    private SearchSynonyms(Map<String, Set<String>> expansions) {
        this.expansions = expansions;
    }

    public static SearchSynonyms empty() {
        return new SearchSynonyms(Map.of());
    }

    public static SearchSynonyms of(String... lines) {
        return parse(List.of(lines));
    }

    /**
     * 클래스패스의 사전 파일을 읽는다. 파일이 없거나 형식이 맞지 않으면 기동에 실패한다.
     */
    public static SearchSynonyms load(String path) {
        try (InputStream input = SearchSynonyms.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("동의어 사전 파일이 없습니다. path=" + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return parse(reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException("동의어 사전을 읽지 못했습니다. path=" + path, e);
        }
    }

    static SearchSynonyms parse(List<String> lines) {
        Map<String, Set<String>> expansions = new HashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = index + 1;
            if (line.contains(ONE_WAY)) {
                String[] sides = line.split(ONE_WAY, -1);
                if (sides.length != 2) {
                    throw invalid(lineNumber, "=>는 한 번만 쓸 수 있습니다");
                }
                List<String> from = terms(sides[0], lineNumber);
                List<String> to = terms(sides[1], lineNumber);
                for (String word : from) {
                    add(expansions, word, to, lineNumber);
                }
            } else {
                List<String> group = terms(line, lineNumber);
                if (group.size() < 2) {
                    throw invalid(lineNumber, "같은 뜻의 말이 두 개 이상 있어야 합니다");
                }
                for (String word : group) {
                    add(expansions, word, group, lineNumber);
                }
            }
        }
        return new SearchSynonyms(Map.copyOf(expansions));
    }

    /**
     * 단어와 같은 뜻으로 넓힌 말을 돌려준다. 단어 자신은 넣지 않는다. 사전에 없으면 빈 목록이다.
     */
    public List<String> expand(String word) {
        Set<String> found = expansions.get(key(word));
        if (found == null) {
            return List.of();
        }
        String self = key(word);
        return found.stream().filter(term -> !key(term).equals(self)).toList();
    }

    private static void add(Map<String, Set<String>> expansions, String word, List<String> terms, int lineNumber) {
        if (word.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(lineNumber, "찾는 말에는 공백을 쓸 수 없습니다");
        }
        expansions.computeIfAbsent(key(word), ignored -> new LinkedHashSet<>()).addAll(terms);
    }

    private static List<String> terms(String text, int lineNumber) {
        List<String> terms = new ArrayList<>();
        for (String term : text.split(",")) {
            String stripped = term.strip().replaceAll("\\s+", " ");
            if (stripped.isEmpty()) {
                throw invalid(lineNumber, "빈 말이 있습니다");
            }
            terms.add(stripped);
        }
        return terms;
    }

    private static String key(String word) {
        return word.strip().toLowerCase(Locale.ROOT);
    }

    private static IllegalStateException invalid(int lineNumber, String reason) {
        return new IllegalStateException("동의어 사전 형식이 맞지 않습니다. line=" + lineNumber + " " + reason);
    }
}
