package com.bbcc.kidly.boundedcontext.settlement.out.holiday;

import com.bbcc.kidly.boundedcontext.settlement.app.port.HolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.HolidayLookupException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 한국천문연구원 특일정보 API(getRestDeInfo)로 한 달의 공휴일을 조회한다. 응답은 XML이다.
 * 요청 URL에 인증키가 들어 있어, 스프링 예외(메시지에 URL이 들어 있다)는 종류만 남기고 원인으로 붙이지 않는다.
 */
@Slf4j
public class KasiHolidayCalendar implements HolidayCalendar {

    private static final String PATH =
        "/getRestDeInfo?serviceKey={serviceKey}&solYear={year}&solMonth={month}&numOfRows=50&pageNo=1";
    private static final String NORMAL = "00";
    private static final String HOLIDAY = "Y";
    // 외부 엔티티 · DTD를 읽지 않게 DOCTYPE 선언 자체를 막는다(XXE 방지)
    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    private final RestClient restClient;
    private final String serviceKey;

    public KasiHolidayCalendar(RestClient.Builder builder, SettlementHolidayProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.serviceKey = properties.serviceKey();
        if (!StringUtils.hasText(serviceKey)) {
            log.warn("공휴일 API 인증키(HOLIDAY_API_SERVICE_KEY)가 없어 영업일을 판단할 수 없습니다");
        }
    }

    @Override
    public Set<LocalDate> holidaysIn(YearMonth month) {
        if (!StringUtils.hasText(serviceKey)) {
            throw new HolidayLookupException("공휴일 API 인증키가 없습니다");
        }
        return parse(fetch(month));
    }

    private byte[] fetch(YearMonth month) {
        try {
            byte[] body = restClient.get()
                .uri(PATH, serviceKey, month.getYear(), "%02d".formatted(month.getMonthValue()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new HolidayLookupException("공휴일 조회 응답이 오류입니다: HTTP " + response.getStatusCode().value());
                })
                .body(byte[].class);
            if (body == null) {
                throw new HolidayLookupException("공휴일 조회 응답 본문이 없습니다");
            }
            return body;
        } catch (RestClientException e) {
            // 연결 실패 · 시간 초과 등. 메시지에 인증키가 든 URL이 있어 종류만 남긴다
            throw new HolidayLookupException("공휴일 조회에 실패했습니다: " + e.getClass().getSimpleName());
        }
    }

    private static Set<LocalDate> parse(byte[] body) {
        Document document;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(DISALLOW_DOCTYPE, true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            // 기본 처리기는 해석 오류를 표준 오류로 찍는다. 예외로만 알린다
            builder.setErrorHandler(new DefaultHandler());
            document = builder.parse(new ByteArrayInputStream(body));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new HolidayLookupException("공휴일 조회 응답을 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
        var root = document.getDocumentElement();
        var resultCode = text(root, "resultCode");
        if (!NORMAL.equals(resultCode)) {
            throw new HolidayLookupException("공휴일 조회 결과가 정상이 아닙니다: resultCode " + resultCode);
        }
        Set<LocalDate> holidays = new HashSet<>();
        NodeList items = root.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            var item = (Element) items.item(i);
            if (HOLIDAY.equals(text(item, "isHoliday"))) {
                holidays.add(locdate(text(item, "locdate")));
            }
        }
        return Set.copyOf(holidays);
    }

    private static String text(Element parent, String tag) {
        var node = parent.getElementsByTagName(tag).item(0);
        if (node == null) {
            throw new HolidayLookupException("공휴일 조회 응답에 " + tag + "가 없습니다");
        }
        return node.getTextContent().strip();
    }

    // locdate는 yyyyMMdd
    private static LocalDate locdate(String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new HolidayLookupException("공휴일 조회 응답의 locdate 형식이 맞지 않습니다: " + value);
        }
    }
}
