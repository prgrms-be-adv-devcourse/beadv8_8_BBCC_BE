package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementHoliday;
import com.bbcc.kidly.boundedcontext.settlement.out.holiday.KasiSettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.out.holiday.SettlementHolidayProperties;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class KasiSettlementHolidayCalendarTest {

    private static final String BASE_URL = "https://holiday.test/SpcdeInfoService";
    // 실제 키와 같은 64자 16진수 모양의 가짜 키
    private static final String FAKE_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Year YEAR = Year.of(2026);
    private static final String YEAR_URL = BASE_URL + "/getRestDeInfo?serviceKey=" + FAKE_KEY
        + "&solYear=2026&numOfRows=100&pageNo=1";

    private MockRestServiceServer server;
    private KasiSettlementHolidayCalendar calendar;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        calendar = new KasiSettlementHolidayCalendar(builder, properties(FAKE_KEY));
    }

    @Test
    @DisplayName("그해의 공휴일 날짜를 모은다. solMonth 없이 연도만 보낸다")
    void collectsHolidays() {
        expectYear().andRespond(withSuccess(response("""
            <item><dateKind>01</dateKind><dateName>1월1일</dateName><isHoliday>Y</isHoliday>\
            <locdate>20260101</locdate><seq>1</seq></item>
            <item><dateKind>01</dateKind><dateName>대체공휴일(삼일절)</dateName><isHoliday>Y</isHoliday>\
            <locdate>20260302</locdate><seq>1</seq></item>""", 2), MediaType.APPLICATION_XML));

        var holidays = calendar.holidaysIn(YEAR);

        assertThat(holidays).containsExactly(new SettlementHoliday(LocalDate.of(2026, 1, 1), "1월1일"),
            new SettlementHoliday(LocalDate.of(2026, 3, 2), "대체공휴일(삼일절)"));
        server.verify();
    }

    @Test
    @DisplayName("항목이 1건이어도 같은 모양으로 읽는다")
    void collectsSingleHoliday() {
        expectYear().andRespond(withSuccess(response("""
            <item><dateKind>01</dateKind><dateName>대체공휴일(삼일절)</dateName><isHoliday>Y</isHoliday>\
            <locdate>20260302</locdate><seq>1</seq></item>""", 1), MediaType.APPLICATION_XML));

        assertThat(calendar.holidaysIn(YEAR))
            .containsExactly(new SettlementHoliday(LocalDate.of(2026, 3, 2), "대체공휴일(삼일절)"));
    }

    @Test
    @DisplayName("isHoliday가 Y인 항목만 공휴일로 본다")
    void skipsNonHoliday() {
        expectYear().andRespond(withSuccess(response("""
            <item><dateKind>01</dateKind><dateName>대체공휴일(삼일절)</dateName><isHoliday>Y</isHoliday>\
            <locdate>20260302</locdate><seq>1</seq></item>
            <item><dateKind>01</dateKind><dateName>기념일</dateName><isHoliday>N</isHoliday>\
            <locdate>20260305</locdate><seq>1</seq></item>""", 2), MediaType.APPLICATION_XML));

        assertThat(calendar.holidaysIn(YEAR))
            .containsExactly(new SettlementHoliday(LocalDate.of(2026, 3, 2), "대체공휴일(삼일절)"));
    }

    @Test
    @DisplayName("같은 날짜의 공휴일 이름을 응답 순서대로 합친다")
    void combinesNamesOnSameDay() {
        expectYear().andRespond(withSuccess(response("""
            <item><dateName>어린이날</dateName><isHoliday>Y</isHoliday><locdate>20260505</locdate></item>
            <item><dateName>부처님오신날</dateName><isHoliday>Y</isHoliday><locdate>20260505</locdate></item>""", 2),
            MediaType.APPLICATION_XML));
        assertThat(calendar.holidaysIn(YEAR)).containsExactly(
            new SettlementHoliday(LocalDate.of(2026, 5, 5), "어린이날, 부처님오신날"));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"<items/>", "<items></items>", ""})
    @DisplayName("공휴일이 없는 달은 빈 집합을 돌려준다")
    void emptyMonth(String items) {
        expectYear().andRespond(withSuccess("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>\
            <body>%s<numOfRows>100</numOfRows><pageNo>1</pageNo><totalCount>0</totalCount></body></response>"""
            .formatted(items), MediaType.APPLICATION_XML));

        assertThat(calendar.holidaysIn(YEAR)).isEmpty();
    }

    @Test
    @DisplayName("resultCode가 00이 아니면 결과 코드를 담아 실패한다")
    void failsOnErrorResultCode() {
        expectYear().andRespond(withSuccess("""
            <response><header><resultCode>30</resultCode>\
            <resultMsg>SERVICE KEY IS NOT REGISTERED ERROR.</resultMsg></header></response>""",
            MediaType.APPLICATION_XML));

        assertFailsWithoutKey(() -> calendar.holidaysIn(YEAR), "resultCode 30");
    }

    @Test
    @DisplayName("resultCode가 없는 응답이면 실패한다")
    void failsOnMissingResultCode() {
        expectYear().andRespond(withSuccess("""
            <OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode>\
            </cmmMsgHeader></OpenAPI_ServiceResponse>""", MediaType.APPLICATION_XML));

        assertFailsWithoutKey(() -> calendar.holidaysIn(YEAR), "resultCode");
    }

    @Test
    @DisplayName("HTTP 5xx면 상태 코드를 담아 실패한다")
    void failsOnServerError() {
        expectYear().andRespond(withServerError());

        assertFailsWithoutKey(() -> calendar.holidaysIn(YEAR), "HTTP 500");
    }

    @Test
    @DisplayName("시간 초과면 실패한다. 스프링 예외 메시지에 든 URL(인증키)은 남기지 않는다")
    void failsOnTimeout() {
        expectYear().andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertFailsWithoutKey(() -> calendar.holidaysIn(YEAR), "ResourceAccessException");
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "<response><header>",
        "<response><header><resultCode>00</resultCode></header><body><items><item><isHoliday>Y</isHoliday>"
            + "<locdate>2026-03-02</locdate></item></items></body></response>",
        "<response><header><resultCode>00</resultCode></header><body><items><item><isHoliday>Y</isHoliday>"
            + "</item></items></body></response>"})
    @DisplayName("깨진 XML이거나 항목의 날짜를 읽지 못하면 실패한다")
    void failsOnMalformedBody(String body) {
        expectYear().andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        assertFailsWithoutKey(() -> calendar.holidaysIn(YEAR), "공휴일 조회 응답");
    }

    @Test
    @DisplayName("DOCTYPE이 든 응답은 읽지 않는다(외부 엔티티 차단)")
    void rejectsDoctype() {
        expectYear().andRespond(withSuccess("""
            <?xml version="1.0"?>
            <!DOCTYPE response [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <response><header><resultCode>&xxe;</resultCode></header></response>""",
            MediaType.APPLICATION_XML));

        assertFailsWithoutKey(() -> calendar.holidaysIn(YEAR), "SAXParseException");
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " "})
    @DisplayName("인증키가 없으면 호출하지 않고 실패한다")
    void failsWithoutServiceKey(String serviceKey) {
        RestClient.Builder builder = RestClient.builder();
        var noKeyServer = MockRestServiceServer.bindTo(builder).build();
        var noKeyCalendar = new KasiSettlementHolidayCalendar(builder, properties(serviceKey));

        assertThatThrownBy(() -> noKeyCalendar.holidaysIn(YEAR))
            .isInstanceOf(SettlementHolidayLookupException.class)
            .hasMessageContaining("인증키가 없습니다");
        noKeyServer.verify();
    }

    private ResponseActions expectYear() {
        return server.expect(requestTo(YEAR_URL)).andExpect(method(HttpMethod.GET));
    }

    // 원인 예외(메시지에 URL이 든다)를 붙이지 않았는지까지 확인한다
    private static void assertFailsWithoutKey(ThrowingCallable call, String expectedMessage) {
        assertThatThrownBy(call)
            .isInstanceOf(SettlementHolidayLookupException.class)
            .hasMessageContaining(expectedMessage)
            .hasNoCause()
            .message().doesNotContain(FAKE_KEY);
    }

    private static String response(String items, int totalCount) {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>\
            <body><items>%s</items><numOfRows>100</numOfRows><pageNo>1</pageNo><totalCount>%d</totalCount>\
            </body></response>""".formatted(items, totalCount);
    }

    private static SettlementHolidayProperties properties(String serviceKey) {
        return new SettlementHolidayProperties(BASE_URL, serviceKey, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }
}
