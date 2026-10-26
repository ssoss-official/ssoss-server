package com.ssoss.ssossbackend.content.infrastructure.ai;

import java.util.List;

import com.ssoss.ssossbackend.content.domain.model.Channel;
import com.ssoss.ssossbackend.content.domain.model.GenerationMaterial;
import com.ssoss.ssossbackend.content.domain.model.Purpose;
import com.ssoss.ssossbackend.content.domain.model.StoreMaterial;
import com.ssoss.ssossbackend.content.domain.model.StyleSource;
import com.ssoss.ssossbackend.content.domain.model.Tone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenerationPromptComposer")
class GenerationPromptComposerTest {

    private static final StoreMaterial NO_STORE = new StoreMaterial(null, null, null, null,
        List.of(), null, null, List.of(), List.of());

    private final GenerationPromptComposer composer = new GenerationPromptComposer(new StoreSectionComposer(),
        new StyleSourceSectionComposer());

    private static GenerationMaterial material(Channel channel, boolean photoGuideChecked, StoreMaterial store,
        StyleSource styleSource) {
        return new GenerationMaterial(channel, Purpose.INFORMATION, Tone.CASUAL, "주말 이벤트", null, List.of(),
            photoGuideChecked, store, styleSource);
    }

    private static GenerationMaterial materialWithStore(StoreMaterial store) {
        return material(Channel.BLOG, false, store, StyleSource.none());
    }

    private static GenerationMaterial materialWithStyleSource(StyleSource styleSource) {
        return material(Channel.BLOG, false, NO_STORE, styleSource);
    }

    private static GenerationMaterial materialWithPhotoGuide(Channel channel) {
        return material(channel, true, NO_STORE, StyleSource.none());
    }

    private static GenerationMaterial materialWithoutPhotoGuide(Channel channel) {
        return material(channel, false, NO_STORE, StyleSource.none());
    }

    @Nested
    @DisplayName("역할 지시")
    class RoleInstruction {

        @Test
        @DisplayName("매장 정보가 비어도 업종을 카페·베이커리로 못박는다")
        void carriesStoreType_whenStoreEmpty() {
            String prompt = composer.compose(materialWithStore(NO_STORE));

            assertThat(prompt).contains("너는 카페·베이커리 매장의 홍보 콘텐츠를 대신 써 주는 전문 카피라이터다.");
        }
    }

    @Nested
    @DisplayName("본문 형식 절")
    class BodyFormatSection {

        @Test
        @DisplayName("문단 안에서 문장마다 줄을 바꾸라는 지시가 실린다")
        void carriesLineBreakPerSentence() {
            String prompt = composer.compose(materialWithStore(NO_STORE));

            assertThat(prompt).contains("문단 안에서는 문장 하나가 끝날 때마다 줄을 바꾼다.");
        }

        @Test
        @DisplayName("문단 안 줄바꿈을 막던 지시는 실리지 않는다")
        void omitsLineBreakBan() {
            String prompt = composer.compose(materialWithStore(NO_STORE));

            assertThat(prompt).doesNotContain("원소 안에서는 줄바꿈을 쓰지 않고");
        }
    }

    @Nested
    @DisplayName("강조 내용 절")
    class EmphasisSection {

        @Test
        @DisplayName("강조 내용에 매장으로 연결하라는 지시가 함께 실린다")
        void carriesLinkingInstruction() {
            String prompt = composer.compose(materialWithStore(NO_STORE));

            assertThat(prompt).contains("""
                매장과 무관해 보이는 내용이라도 이 매장을 알리는 이야기로 연결해 풀어내되, [매장 정보]에 없는 사실은 지어내지 않는다.
                주말 이벤트""");
        }
    }

    @Nested
    @DisplayName("매장 정보 절")
    class StoreSection {

        @Test
        @DisplayName("매장 정보를 다 채우면 값이 모두 실린다")
        void carriesEveryField_whenStoreFullyWritten() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial("보니스커피", "카페", "서울 중구 을지로 100",
                "을지로 크루아상 카페", List.of("월요일", "화요일"), "09:00", "22:00",
                List.of("크루아상", "아메리카노"), List.of("포장 가능", "주차 가능"))));

            assertThat(prompt).contains("""
                [매장 정보]
                아래는 이 매장에 대한 사실이다. 여기 없는 사실은 지어내지 않는다.
                매장명: 보니스커피
                매장 유형: 카페
                주소: 서울 중구 을지로 100
                한 줄 소개: 을지로 크루아상 카페
                영업일: 월요일, 화요일
                영업 시간: 09:00~22:00
                대표 메뉴: 크루아상, 아메리카노
                편의 시설: 포장 가능, 주차 가능""");
        }

        @Test
        @DisplayName("운영 정보를 안 채우면 그 줄이 통째로 빠진다")
        void omitsOperationLines_whenOperationInfoMissing() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial("보니스커피", "카페", "서울 중구 을지로 100",
                "을지로 크루아상 카페", List.of(), null, null, List.of(), List.of())));

            assertThat(prompt)
                .contains("매장명: 보니스커피")
                .doesNotContain("영업일:", "영업 시간:", "대표 메뉴:", "편의 시설:");
        }

        @Test
        @DisplayName("영업 시각은 시작과 종료가 둘 다 있어야 실린다")
        void omitsBusinessHours_whenOnlyOpenTimeWritten() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial("보니스커피", null, null, null,
                List.of(), "09:00", null, List.of(), List.of())));

            assertThat(prompt).doesNotContain("영업 시간:");
        }

        @Test
        @DisplayName("빈 항목만 줄에서 빠지고 채운 항목은 남는다")
        void keepsWrittenLinesOnly_whenPartiallyWritten() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial("보니스커피", null, null, null,
                List.of(), null, null, List.of("크루아상"), List.of())));

            assertThat(prompt)
                .contains("매장명: 보니스커피")
                .contains("대표 메뉴: 크루아상")
                .doesNotContain("매장 유형:", "주소:", "한 줄 소개:");
        }

        @Test
        @DisplayName("기본 정보를 건너뛰고 운영 정보만 채워도 그 값이 실린다")
        void carriesOperationLines_whenOnlyOperationInfoWritten() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial(null, null, null, null,
                List.of("월요일", "화요일"), "09:00", "22:00", List.of("크루아상"), List.of("포장 가능"))));

            assertThat(prompt)
                .contains("영업일: 월요일, 화요일")
                .contains("영업 시간: 09:00~22:00")
                .contains("대표 메뉴: 크루아상")
                .contains("편의 시설: 포장 가능")
                .doesNotContain("매장명:", "매장 정보가 제공되지 않았다");
        }

        @Test
        @DisplayName("매장명이 공백뿐이면 매장명 줄만 빠진다")
        void omitsNameLine_whenNameBlank() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial("   ", "카페", null, null,
                List.of(), null, null, List.of(), List.of())));

            assertThat(prompt)
                .contains("매장 유형: 카페")
                .doesNotContain("매장명:", "매장 정보가 제공되지 않았다");
        }

        @Test
        @DisplayName("모든 항목이 비면 매장 정보가 없다는 문구가 실린다")
        void carriesNoStoreNotice_whenNothingWritten() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial(null, null, null, null,
                List.of(), null, null, List.of(), List.of())));

            assertThat(prompt)
                .contains("매장 정보가 제공되지 않았다")
                .doesNotContain("매장명:");
        }

        @Test
        @DisplayName("영업 시작 시각만 있으면 실을 값이 없어 매장 정보가 없는 것으로 본다")
        void carriesNoStoreNotice_whenOnlyOpenTimeWrittenAndNothingElse() {
            String prompt = composer.compose(materialWithStore(new StoreMaterial(null, null, null, null,
                List.of(), "09:00", null, List.of(), List.of())));

            assertThat(prompt).contains("매장 정보가 제공되지 않았다");
        }
    }

    @Nested
    @DisplayName("참고 글 절")
    class StyleSourceSection {

        @Test
        @DisplayName("참고 글이 없으면 참고 글 절과 참고 범위 절이 통째로 빠진다")
        void omitsSections_whenNoStyleSource() {
            String prompt = composer.compose(materialWithStore(NO_STORE));

            assertThat(prompt).doesNotContain("[참고 글]", "[참고 범위]");
        }

        @Test
        @DisplayName("참고 글이 있으면 제목·본문과 참고 범위 지시가 함께 실린다")
        void carriesTitleBodyAndScope_whenStyleSourceGiven() {
            String prompt = composer.compose(materialWithStyleSource(
                new StyleSource("을지로 크루아상 맛집", "겹겹이 살아있는 결을 만나 보세요.")));

            assertThat(prompt)
                .contains("[참고 글]")
                .contains("제목: 을지로 크루아상 맛집")
                .contains("겹겹이 살아있는 결을 만나 보세요.")
                .contains("[참고 범위]")
                .contains("말투와 문장 길이뿐이다");
        }

        @Test
        @DisplayName("참고 글에서 줄과 문단 나누는 방식은 가져오지 않는다")
        void omitsFormatFromScope_whenStyleSourceGiven() {
            String prompt = composer.compose(materialWithStyleSource(
                new StyleSource("을지로 크루아상 맛집", "겹겹이 살아있는 결을 만나 보세요.")));

            assertThat(prompt)
                .contains("줄과 문단을 나누는 방식은 [본문 형식]과 [채널]을 따르고, 참고 글의 모양은 따르지 않는다.")
                .doesNotContain("말투·문장 구성·분량뿐이다");
        }

        @Test
        @DisplayName("제목 없는 채널의 참고 글이면 제목 줄만 빠진다")
        void omitsTitleLine_whenStyleSourceHasNoTitle() {
            String prompt = composer.compose(materialWithStyleSource(new StyleSource(null, "겹겹이 살아있는 결을 만나 보세요.")));

            assertThat(prompt)
                .contains("[참고 글]")
                .contains("겹겹이 살아있는 결을 만나 보세요.")
                .doesNotContain("제목:");
        }
    }

    @Nested
    @DisplayName("채널별 정책")
    class ChannelPolicy {

        @Test
        @DisplayName("블로그면 블로그 지시문과 사진 가이드 2~4곳, 해시태그 10개 지시가 실린다")
        void carriesBlogPolicy_whenChannelBlog() {
            String prompt = composer.compose(materialWithPhotoGuide(Channel.BLOG));

            assertThat(prompt)
                .contains("네이버 블로그에 올릴 글을 쓴다.")
                .contains("사진이 들어가면 좋을 자리를 2~4곳 골라")
                .doesNotContain("본문 맨 위에 붙일")
                .contains("해시태그는 10개 만들고");
        }

        @Test
        @DisplayName("인스타그램이면 인스타그램 지시문과 본문 맨 위 사진 가이드 1~2개, 해시태그 10개 지시가 실린다")
        void carriesInstagramPolicy_whenChannelInstagram() {
            String prompt = composer.compose(materialWithPhotoGuide(Channel.INSTAGRAM));

            assertThat(prompt)
                .contains("인스타그램 피드에 올릴 캡션을 쓴다.")
                .contains("본문 맨 위에 붙일 사진 안내를 1~2개 만들어")
                .doesNotContain("사진이 들어가면 좋을 자리를")
                .contains("해시태그는 10개 만들고");
        }

        @Test
        @DisplayName("당근 비즈면 당근 비즈 지시문과 본문 맨 위 사진 가이드 1개가 실리고 해시태그를 만들지 않는다")
        void carriesDaangnBizPolicy_whenChannelDaangnBiz() {
            String prompt = composer.compose(materialWithPhotoGuide(Channel.DAANGN_BIZ));

            assertThat(prompt)
                .contains("당근 비즈프로필 소식에 올릴 글을 쓴다.")
                .contains("본문 맨 위에 붙일 사진 안내를 1개 만들어")
                .doesNotContain("사진이 들어가면 좋을 자리를")
                .contains("해시태그를 만들지 않는다");
        }

        @Test
        @DisplayName("스레드면 스레드 지시문과 본문 맨 위 사진 가이드 1개, 해시태그 최대 3개 지시가 실린다")
        void carriesThreadsPolicy_whenChannelThreads() {
            String prompt = composer.compose(materialWithPhotoGuide(Channel.THREADS));

            assertThat(prompt)
                .contains("스레드에 올릴 게시물을 쓴다.")
                .contains("본문 맨 위에 붙일 사진 안내를 1개 만들어")
                .doesNotContain("사진이 들어가면 좋을 자리를")
                .contains("해시태그는 어울릴 때만 최대 3개까지 만들고");
        }

        @Test
        @DisplayName("채널마다 한 문단으로 묶을 문장 수가 실린다")
        void carriesParagraphBundle_forEveryChannel() {
            assertThat(composer.compose(materialWithoutPhotoGuide(Channel.BLOG)))
                .contains("한 문단은 3~4문장으로 묶고,");
            assertThat(composer.compose(materialWithoutPhotoGuide(Channel.INSTAGRAM)))
                .contains("한 문단은 2~3문장으로 묶고,")
                .contains("이모지를 적절히 섞는다.");
            assertThat(composer.compose(materialWithoutPhotoGuide(Channel.DAANGN_BIZ)))
                .contains("한 문단은 2~3문장으로 묶고,");
            assertThat(composer.compose(materialWithoutPhotoGuide(Channel.THREADS)))
                .contains("한 문단은 2~3문장으로 묶고,");
        }

        @Test
        @DisplayName("어느 채널에도 문단 개수 상한을 정하던 지시는 실리지 않는다")
        void omitsParagraphCountCeiling_forEveryChannel() {
            for (Channel channel : Channel.values()) {
                assertThat(composer.compose(materialWithoutPhotoGuide(channel)))
                    .doesNotContain("문단은 4~6개로", "문단은 2~4개로", "문단은 2~3개로", "각 문단을 200자 이상으로");
            }
        }

        @Test
        @DisplayName("채널마다 문단 개수 하한이 실려 본문이 한 문단으로 오지 않는다")
        void carriesParagraphCountFloor_forEveryChannel() {
            assertThat(composer.compose(materialWithoutPhotoGuide(Channel.BLOG)))
                .contains("문단은 4개 이상으로 나눈다.");
            for (Channel channel : List.of(Channel.INSTAGRAM, Channel.DAANGN_BIZ, Channel.THREADS)) {
                assertThat(composer.compose(materialWithoutPhotoGuide(channel)))
                    .contains("문단은 2개 이상으로 나눈다.");
            }
        }
    }

    @Nested
    @DisplayName("절 구분")
    class SectionSeparator {

        @Test
        @DisplayName("절과 절 사이는 빈 줄 없이 줄바꿈 하나로 이어진다")
        void joinsSectionsWithSingleLineBreak() {
            String prompt = composer.compose(materialWithoutPhotoGuide(Channel.BLOG));

            assertThat(prompt).contains("문단은 4개 이상으로 나눈다.\n[본문 형식]");
        }
    }

    @Nested
    @DisplayName("사진 가이드 절")
    class PhotoGuideSection {

        @Test
        @DisplayName("사진 가이드를 체크하지 않으면 절이 통째로 빠진다")
        void omitsSection_whenPhotoGuideUnchecked() {
            String prompt = composer.compose(materialWithoutPhotoGuide(Channel.BLOG));

            assertThat(prompt).doesNotContain("[사진 가이드]", "<photo-guide/>");
        }

        @Test
        @DisplayName("마커를 문단 사이에 낀 원소로 받으라는 지시가 실린다")
        void carriesMarkerAsOwnElement_whenPhotoGuideChecked() {
            String prompt = composer.compose(materialWithPhotoGuide(Channel.BLOG));

            assertThat(prompt)
                .contains("<photo-guide/> 마커만 담은 원소를 문단 사이에 끼운다.")
                .doesNotContain("줄을 문단 안에 끼운다");
        }
    }
}
