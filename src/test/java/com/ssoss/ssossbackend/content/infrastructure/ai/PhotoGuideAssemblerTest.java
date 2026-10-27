package com.ssoss.ssossbackend.content.infrastructure.ai;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사진 가이드 조립")
class PhotoGuideAssemblerTest {

    private final PhotoGuideAssembler assembler = new PhotoGuideAssembler(new ParagraphJoiner());

    @Nested
    @DisplayName("문단 이어붙이기")
    class ParagraphJoining {

        @Test
        @DisplayName("문단 원소가 빈 줄로 이어져 본문이 된다")
        void joinsParagraphsWithBlankLine() {
            String assembled = assembler.assemble(List.of("첫 문단", "둘째 문단"), List.of());

            assertThat(assembled).isEqualTo("첫 문단\n\n둘째 문단");
        }

        @Test
        @DisplayName("마커 원소는 앞뒤가 빈 줄 없이 자기 줄에 태그로 조립된다")
        void assemblesMarkerElementOnItsOwnLine() {
            List<String> paragraphs = List.of("첫 문단", "<photo-guide/>", "둘째 문단");
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("시그니처 메뉴", "위에서 내려다보며 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled).isEqualTo("""
                첫 문단
                <photo-guide title="시그니처 메뉴" description="위에서 내려다보며 찍기"/>
                둘째 문단""");
        }

        @Test
        @DisplayName("문단끼리는 빈 줄로 이어지고 마커 앞뒤만 줄바꿈 하나로 좁혀진다")
        void keepsBlankLineBetweenParagraphsOnly() {
            List<String> paragraphs = List.of("첫 문단", "둘째 문단", "<photo-guide/>", "셋째 문단");
            List<PhotoGuideOutput> guides = List.of(new PhotoGuideOutput("시그니처 메뉴", "위에서 내려다보며 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled).isEqualTo("""
                첫 문단

                둘째 문단
                <photo-guide title="시그니처 메뉴" description="위에서 내려다보며 찍기"/>
                셋째 문단""");
        }

        @Test
        @DisplayName("마커가 맨 앞이나 맨 뒤에 와도 이웃과 줄바꿈 하나로 이어진다")
        void joinsMarkerAtBothEndsWithSingleLineBreak() {
            List<String> paragraphs = List.of("<photo-guide/>", "첫 문단", "<photo-guide/>");
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("매장 외관", "간판이 보이게 찍기"),
                new PhotoGuideOutput("창가 자리", "햇빛이 드는 낮에 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled).isEqualTo("""
                <photo-guide title="매장 외관" description="간판이 보이게 찍기"/>
                첫 문단
                <photo-guide title="창가 자리" description="햇빛이 드는 낮에 찍기"/>""");
        }

        @Test
        @DisplayName("빈 원소와 공백 원소는 버려져 빈 줄이 겹치지 않는다")
        void dropsBlankParagraphs() {
            String assembled = assembler.assemble(List.of("첫 문단", "", "   ", "둘째 문단"), List.of());

            assertThat(assembled).isEqualTo("첫 문단\n\n둘째 문단");
        }

        @Test
        @DisplayName("문단이 하나도 없으면 빈 본문이 된다")
        void returnsEmptyBodyWithoutParagraphs() {
            String assembled = assembler.assemble(List.of(), List.of());

            assertThat(assembled).isEmpty();
        }

        @Test
        @DisplayName("문단 배열이 없으면 그대로 null 이 반환된다")
        void returnsNullWhenParagraphsAreNull() {
            String assembled = assembler.assemble(null, List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled).isNull();
        }
    }

    @Nested
    @DisplayName("마커와 카드 짝짓기")
    class Pairing {

        @Test
        @DisplayName("마커와 카드 수가 같으면 순서대로 태그로 조립된다")
        void assemblesInOrder() {
            List<String> paragraphs = List.of("첫 문단", "<photo-guide/>", "둘째 문단", "<photo-guide/>");
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("시그니처 메뉴", "위에서 내려다보며 찍기"),
                new PhotoGuideOutput("매장 외관", "간판이 보이게 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled).isEqualTo("""
                첫 문단
                <photo-guide title="시그니처 메뉴" description="위에서 내려다보며 찍기"/>
                둘째 문단
                <photo-guide title="매장 외관" description="간판이 보이게 찍기"/>""");
        }

        @Test
        @DisplayName("마커가 카드보다 많으면 짝 없는 마커는 지워진다")
        void removesUnpairedMarkers() {
            List<String> paragraphs = List.of("앞 <photo-guide/> 뒤 <photo-guide/> 끝");
            List<PhotoGuideOutput> guides = List.of(new PhotoGuideOutput("창가 자리", "햇빛이 드는 낮에 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled)
                .isEqualTo("앞 <photo-guide title=\"창가 자리\" description=\"햇빛이 드는 낮에 찍기\"/> 뒤  끝");
        }

        @Test
        @DisplayName("카드가 마커보다 많으면 자리 없는 카드는 버려진다")
        void dropsGuidesWithoutMarker() {
            List<String> paragraphs = List.of("앞 <photo-guide/> 끝");
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("사장님", "인사하는 모습 찍기"),
                new PhotoGuideOutput("신메뉴", "가까이서 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled)
                .isEqualTo("앞 <photo-guide title=\"사장님\" description=\"인사하는 모습 찍기\"/> 끝")
                .doesNotContain("신메뉴");
        }

        @Test
        @DisplayName("카드가 없으면 마커가 모두 지워진다")
        void removesAllMarkersWithoutGuides() {
            String assembled = assembler.assemble(List.of("앞 <photo-guide/> 뒤"), List.of());

            assertThat(assembled).isEqualTo("앞  뒤");
        }

        @Test
        @DisplayName("카드 목록이 null 이어도 마커가 지워진다")
        void removesMarkersWhenGuidesAreNull() {
            String assembled = assembler.assemble(List.of("앞 <photo-guide/> 뒤"), null);

            assertThat(assembled).isEqualTo("앞  뒤");
        }

        @Test
        @DisplayName("제목이 없는 카드는 버려지고 마커만 지워진다")
        void dropsGuideWithoutTitle() {
            List<PhotoGuideOutput> guides = List.of(new PhotoGuideOutput(null, "위에서 내려다보며 찍기"));

            String assembled = assembler.assemble(List.of("앞 <photo-guide/> 뒤"), guides);

            assertThat(assembled).isEqualTo("앞  뒤");
        }

        @Test
        @DisplayName("설명이 없는 카드는 버려지고 마커만 지워진다")
        void dropsGuideWithoutDescription() {
            List<PhotoGuideOutput> guides = List.of(new PhotoGuideOutput("시그니처 메뉴", null));

            String assembled = assembler.assemble(List.of("앞 <photo-guide/> 뒤"), guides);

            assertThat(assembled).isEqualTo("앞  뒤");
        }

        @Test
        @DisplayName("가운데 카드가 버려져도 뒤 카드는 자기 마커 자리를 지킨다")
        void keepsLaterGuidesInPlace_whenMiddleGuideIsDropped() {
            List<String> paragraphs = List.of("앞 <photo-guide/> 가운데 <photo-guide/> 또 <photo-guide/> 끝");
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("시그니처 메뉴", "위에서 내려다보며 찍기"),
                new PhotoGuideOutput("매장 외관", "   "),
                new PhotoGuideOutput("창가 자리", "햇빛이 드는 낮에 찍기"));

            String assembled = assembler.assemble(paragraphs, guides);

            assertThat(assembled).isEqualTo(
                "앞 <photo-guide title=\"시그니처 메뉴\" description=\"위에서 내려다보며 찍기\"/> 가운데  또 "
                    + "<photo-guide title=\"창가 자리\" description=\"햇빛이 드는 낮에 찍기\"/> 끝");
        }

        @Test
        @DisplayName("제목이나 설명이 공백뿐인 카드는 버려진다")
        void dropsGuideWithBlankText() {
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("   ", "위에서 내려다보며 찍기"),
                new PhotoGuideOutput("시그니처 메뉴", "   "));

            String assembled = assembler.assemble(List.of("앞 <photo-guide/> 가운데 <photo-guide/> 뒤"), guides);

            assertThat(assembled).isEqualTo("앞  가운데  뒤");
        }

        @Test
        @DisplayName("마커가 없으면 본문이 그대로 유지된다")
        void keepsBodyWithoutMarkers() {
            String assembled = assembler.assemble(List.of("마커가 없는 평범한 본문"),
                List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled).isEqualTo("마커가 없는 평범한 본문");
        }
    }

    @Nested
    @DisplayName("어긋난 마커 관용")
    class MalformedMarker {

        @Test
        @DisplayName("마커에 속성이 딸려 와도 자리로 인정되어 조립된다")
        void pairsMarkerWithAttributes() {
            String assembled = assembler.assemble(List.of("앞 <photo-guide id=\"1\"/> 뒤"),
                List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled).isEqualTo("앞 <photo-guide title=\"메뉴\" description=\"찍기\"/> 뒤");
        }

        @Test
        @DisplayName("마커가 쌍 태그로 오면 여는 쪽만 조립되고 닫는 태그는 지워진다")
        void assemblesOpeningTagAndRemovesClosingTag() {
            String assembled = assembler.assemble(List.of("앞 <photo-guide id=\"1\"></photo-guide> 뒤"),
                List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled)
                .isEqualTo("앞 <photo-guide title=\"메뉴\" description=\"찍기\"/> 뒤")
                .doesNotContain("</photo-guide>");
        }

        @Test
        @DisplayName("짝 없는 닫는 태그는 카드를 쓰지 않고 지워진다")
        void removesStrayClosingTagWithoutConsumingGuide() {
            String assembled = assembler.assemble(List.of("앞 </photo-guide> 가운데 <photo-guide/> 뒤"),
                List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled)
                .isEqualTo("앞  가운데 <photo-guide title=\"메뉴\" description=\"찍기\"/> 뒤");
        }

        @Test
        @DisplayName("이름만 비슷한 태그는 마커로 보지 않고 본문에 그대로 둔다")
        void keepsTagsWithSimilarName() {
            String assembled = assembler.assemble(List.of("앞 <photo-guideline> 가운데 <photo-guide-note/> 뒤"),
                List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled).isEqualTo("앞 <photo-guideline> 가운데 <photo-guide-note/> 뒤");
        }
    }

    @Nested
    @DisplayName("본문 맨 위 배치")
    class OnTop {

        @Test
        @DisplayName("카드가 본문 맨 위에 순서대로 한 줄씩 붙고 그 아래에 본문이 이어진다")
        void placesGuidesAboveBodyInOrder() {
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("시그니처 메뉴", "위에서 내려다보며 찍기"),
                new PhotoGuideOutput("매장 외관", "간판이 보이게 찍기"));

            String assembled = assembler.assembleOnTop(List.of("첫 문단", "둘째 문단"), guides);

            assertThat(assembled).isEqualTo("""
                <photo-guide title="시그니처 메뉴" description="위에서 내려다보며 찍기"/>
                <photo-guide title="매장 외관" description="간판이 보이게 찍기"/>
                첫 문단

                둘째 문단""");
        }

        @Test
        @DisplayName("본문에 마커가 섞여 와도 마커는 지워지고 카드는 맨 위에 붙는다")
        void removesMarkersFromBody() {
            List<String> paragraphs = List.of("첫 문단", "<photo-guide/>", "둘째 문단\n<photo-guide/>", "셋째 <photo-guide/>문단");
            List<PhotoGuideOutput> guides = List.of(new PhotoGuideOutput("창가 자리", "햇빛이 드는 낮에 찍기"));

            String assembled = assembler.assembleOnTop(paragraphs, guides);

            assertThat(assembled).isEqualTo("""
                <photo-guide title="창가 자리" description="햇빛이 드는 낮에 찍기"/>
                첫 문단

                둘째 문단

                셋째 문단""");
        }

        @Test
        @DisplayName("제목이나 설명이 빈 카드는 버려지고 나머지만 맨 위에 붙는다")
        void dropsIncompleteGuides() {
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("   ", "위에서 내려다보며 찍기"),
                new PhotoGuideOutput("매장 외관", "간판이 보이게 찍기"));

            String assembled = assembler.assembleOnTop(List.of("본문"), guides);

            assertThat(assembled).isEqualTo("""
                <photo-guide title="매장 외관" description="간판이 보이게 찍기"/>
                본문""");
        }

        @Test
        @DisplayName("카드 목록이 없으면 본문만 남는다")
        void keepsBodyOnly_whenGuidesAreNull() {
            String assembled = assembler.assembleOnTop(List.of("첫 문단", "둘째 문단"), null);

            assertThat(assembled).isEqualTo("첫 문단\n\n둘째 문단");
        }

        @Test
        @DisplayName("문단이 하나도 없으면 카드가 있어도 빈 본문이 된다")
        void returnsEmptyBody_whenNoParagraphs() {
            String assembled = assembler.assembleOnTop(List.of("<photo-guide/>"),
                List.of(new PhotoGuideOutput("메뉴", "찍기")));

            assertThat(assembled).isEmpty();
        }

        @Test
        @DisplayName("문단 배열이 없으면 그대로 null 이 반환된다")
        void returnsNull_whenParagraphsAreNull() {
            assertThat(assembler.assembleOnTop(null, List.of(new PhotoGuideOutput("메뉴", "찍기")))).isNull();
        }
    }

    @Nested
    @DisplayName("사진 위치에 따른 조립")
    class ByPlacement {

        private final List<String> paragraphs = List.of("첫 문단", "<photo-guide/>", "둘째 문단");
        private final List<PhotoGuideOutput> guides = List.of(new PhotoGuideOutput("창가 자리", "햇빛이 드는 낮에 찍기"));

        @Test
        @DisplayName("위치가 문단 사이면 마커 자리에 태그가 조립된다")
        void assemblesAtMarker_whenBetweenParagraphs() {
            String assembled = assembler.assemble(paragraphs, guides, PhotoGuidePlacement.BETWEEN_PARAGRAPHS);

            assertThat(assembled).isEqualTo("""
                첫 문단
                <photo-guide title="창가 자리" description="햇빛이 드는 낮에 찍기"/>
                둘째 문단""");
        }

        @Test
        @DisplayName("위치가 맨 위면 태그가 본문 맨 위에 붙고 마커는 지워진다")
        void placesOnTop_whenTop() {
            String assembled = assembler.assemble(paragraphs, guides, PhotoGuidePlacement.TOP);

            assertThat(assembled).isEqualTo("""
                <photo-guide title="창가 자리" description="햇빛이 드는 낮에 찍기"/>
                첫 문단

                둘째 문단""");
        }
    }

    @Nested
    @DisplayName("태그 문자열")
    class Tag {

        @Test
        @DisplayName("제목·설명의 따옴표와 꺾쇠는 escape 되어 태그가 깨지지 않는다")
        void escapesAttributeValues() {
            List<PhotoGuideOutput> guides = List.of(
                new PhotoGuideOutput("\"시그니처\" 메뉴", "<b>가까이</b> & 밝게"));

            String assembled = assembler.assemble(List.of("<photo-guide/>"), guides);

            assertThat(assembled).isEqualTo("<photo-guide title=\"&quot;시그니처&quot; 메뉴\" "
                + "description=\"&lt;b&gt;가까이&lt;/b&gt; &amp; 밝게\"/>");
        }

    }
}
