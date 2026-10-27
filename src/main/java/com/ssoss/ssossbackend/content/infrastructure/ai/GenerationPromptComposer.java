package com.ssoss.ssossbackend.content.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;

import com.ssoss.ssossbackend.content.domain.model.GenerationMaterial;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GenerationPromptComposer {

    private static final String ROLE_INSTRUCTION = """
        너는 카페·베이커리 매장의 홍보 콘텐츠를 대신 써 주는 전문 카피라이터다.
        아래 지시를 모두 지켜 콘텐츠를 한국어로 작성한다.""";

    private static final String BODY_FORMAT_SECTION = """
        [본문 형식]
        본문은 paragraphs 배열에 담고, 원소 하나가 문단 하나다.
        문단 안에서는 문장 하나가 끝날 때마다 줄을 바꾼다.
        빈 원소는 넣지 않는다.""";

    private static final String EMPHASIS_SECTION = """
        [강조 내용]
        아래 내용이 콘텐츠의 중심이 되도록 반드시 반영한다.
        매장과 무관해 보이는 내용이라도 이 매장을 알리는 이야기로 연결해 풀어내되, [매장 정보]에 없는 사실은 지어내지 않는다.
        %s""";

    private static final String FORBIDDEN_SECTION = """
        [금지 내용]
        아래 내용은 콘텐츠 어디에도 언급하지 않는다.
        %s""";

    private static final String KEYWORDS_SECTION = """
        [키워드]
        아래 키워드를 본문에 자연스럽게 녹인다.
        %s""";

    private static final String PHOTO_GUIDE_SECTION = """
        [사진 가이드]
        사진이 들어가면 좋을 자리를 %s곳 골라, 그 자리에 <photo-guide/> 마커만 담은 원소를 문단 사이에 끼운다.
        마커와 같은 순서로 photoGuides 배열을 채우고 개수를 정확히 맞춘다.
        title 은 어떤 사진인지 15자 이내로, description 은 어떻게 찍으면 좋은지 40자 이내로 쓴다.
        마커와 배열 외의 방법으로 사진을 언급하지 않는다.""";

    private static final String TOP_PHOTO_GUIDE_SECTION = """
        [사진 가이드]
        본문 맨 위에 붙일 사진 안내를 %s개 만들어 photoGuides 배열에 담는다.
        사진은 본문 위에 따로 붙으므로 paragraphs 에는 <photo-guide/> 마커를 넣지 않는다.
        title 은 어떤 사진인지 15자 이내로, description 은 어떻게 찍으면 좋은지 40자 이내로 쓴다.
        배열 외의 방법으로 사진을 언급하지 않는다.""";

    private static final String SECTION_SEPARATOR = "\n";

    private final StoreSectionComposer storeSectionComposer;
    private final StyleSourceSectionComposer styleSourceSectionComposer;

    String compose(GenerationMaterial material) {
        ChannelPromptPolicy channelPolicy = ChannelPromptPolicy.of(material.channel());
        String purposeInstruction = switch (material.purpose()) {
            case INFORMATION -> "정보성 — 매장과 관련된 유용한 정보를 알려 주는 글을 쓴다.";
            case EVENT_DISCOUNT -> "이벤트/할인 — 이벤트·할인 소식을 알려 방문을 이끄는 글을 쓴다.";
            case NEW_MENU_PROMOTION -> "신메뉴/홍보 — 신메뉴나 매장의 매력을 알리는 홍보 글을 쓴다.";
        };
        String toneInstruction = switch (material.tone()) {
            case CASUAL -> "일상형 — 친구에게 말하듯 편안하고 자연스러운 말투로 쓴다.";
            case EMOTIONAL -> "감성형 — 감성적이고 따뜻한 분위기의 말투로 쓴다.";
            case INFORMATIVE -> "정보형 — 사실을 차분하게 전달하는 신뢰감 있는 말투로 쓴다.";
            case PROMOTIONAL -> "홍보형 — 혜택과 매력을 적극적으로 알리는 말투로 쓴다.";
        };
        List<String> sections = new ArrayList<>();
        sections.add(ROLE_INSTRUCTION);
        sections.add(channelPolicy.instruction());
        sections.add(BODY_FORMAT_SECTION);
        sections.add("[목적]\n" + purposeInstruction);
        sections.add("[톤]\n" + toneInstruction);
        sections.add(EMPHASIS_SECTION.formatted(material.emphasis()));
        if (material.forbidden() != null && !material.forbidden().isBlank()) {
            sections.add(FORBIDDEN_SECTION.formatted(material.forbidden()));
        }
        if (!material.keywords().isEmpty()) {
            sections.add(KEYWORDS_SECTION.formatted(String.join(", ", material.keywords())));
        }
        sections.add(storeSectionComposer.compose(material.store()));
        if (!material.styleSource().isEmpty()) {
            sections.addAll(styleSourceSectionComposer.compose(material.styleSource()));
        }
        if (material.photoGuideChecked()) {
            String photoGuideSection = channelPolicy.photoGuidePlacement() == PhotoGuidePlacement.TOP
                ? TOP_PHOTO_GUIDE_SECTION : PHOTO_GUIDE_SECTION;
            sections.add(photoGuideSection.formatted(channelPolicy.photoGuidePlaceCount()));
        }
        sections.add(channelPolicy.hashtagSection());
        return String.join(SECTION_SEPARATOR, sections);
    }
}
