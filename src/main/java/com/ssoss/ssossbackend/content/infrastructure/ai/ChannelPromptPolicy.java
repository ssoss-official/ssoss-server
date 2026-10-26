package com.ssoss.ssossbackend.content.infrastructure.ai;

import com.ssoss.ssossbackend.content.domain.model.Channel;

record ChannelPromptPolicy(String instruction, String photoGuidePlaceCount, PhotoGuidePlacement photoGuidePlacement,
    String hashtagSection) {

    private static final String BLOG_INSTRUCTION = """
        [채널]
        네이버 블로그에 올릴 글을 쓴다.
        검색 노출을 고려한 40자 이내의 제목을 함께 쓴다.
        본문은 1,000자 이상 2,000자 이하로 쓴다. 1,000자에 못 미치는 본문은 지시를 어긴 것이다.
        한 문단은 3~4문장으로 묶고, 문단은 4개 이상으로 나눈다.""";

    private static final String INSTAGRAM_INSTRUCTION = """
        [채널]
        인스타그램 피드에 올릴 캡션을 쓴다.
        제목 없이 본문만 쓰고, 첫 문장으로 시선을 끈다.
        본문은 300자 이상 700자 이하로 쓴다.
        한 문단은 2~3문장으로 묶고, 문단은 2개 이상으로 나눈다. 이모지를 적절히 섞는다.""";

    private static final String DAANGN_BIZ_INSTRUCTION = """
        [채널]
        당근 비즈프로필 소식에 올릴 글을 쓴다.
        제목 없이 본문만 쓰고, 동네 이웃에게 말을 거는 친근한 문장으로 쓴다.
        본문은 150자 이상 400자 이하로 쓴다.
        한 문단은 2~3문장으로 묶고, 문단은 2개 이상으로 나눈다.""";

    private static final String THREADS_INSTRUCTION = """
        [채널]
        스레드에 올릴 게시물을 쓴다.
        제목 없이 본문만 쓰고, 대화하듯 짧고 편한 문장으로 쓴다.
        본문은 100자 이상 500자 이하로 쓴다.
        한 문단은 2~3문장으로 묶고, 문단은 2개 이상으로 나눈다.""";

    private static final String HASHTAG_SECTION = """
        [해시태그]
        해시태그는 10개 만들고, 각 태그는 #으로 시작하는 공백 없는 한 단어로 쓴다.""";

    private static final String OPTIONAL_HASHTAG_SECTION = """
        [해시태그]
        해시태그는 어울릴 때만 최대 3개까지 만들고, 어울리지 않으면 만들지 않는다.
        만들 때는 #으로 시작하는 공백 없는 한 단어로 쓴다.""";

    private static final String NO_HASHTAG_SECTION = """
        [해시태그]
        해시태그를 만들지 않는다. 본문에도 #으로 시작하는 태그를 쓰지 않는다.""";

    static ChannelPromptPolicy of(Channel channel) {
        return switch (channel) {
            case BLOG -> new ChannelPromptPolicy(BLOG_INSTRUCTION, "2~4", PhotoGuidePlacement.BETWEEN_PARAGRAPHS, HASHTAG_SECTION);
            case INSTAGRAM -> new ChannelPromptPolicy(INSTAGRAM_INSTRUCTION, "1~2", PhotoGuidePlacement.TOP, HASHTAG_SECTION);
            case DAANGN_BIZ -> new ChannelPromptPolicy(DAANGN_BIZ_INSTRUCTION, "1", PhotoGuidePlacement.TOP, NO_HASHTAG_SECTION);
            case THREADS -> new ChannelPromptPolicy(THREADS_INSTRUCTION, "1", PhotoGuidePlacement.TOP, OPTIONAL_HASHTAG_SECTION);
        };
    }
}
