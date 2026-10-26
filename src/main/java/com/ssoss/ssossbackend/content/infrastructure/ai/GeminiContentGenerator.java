package com.ssoss.ssossbackend.content.infrastructure.ai;

import java.time.Duration;
import java.util.List;

import com.ssoss.ssossbackend.content.domain.contract.ContentGenerator;
import com.ssoss.ssossbackend.content.domain.model.GeneratedContent;
import com.ssoss.ssossbackend.content.domain.model.GenerationMaterial;
import com.ssoss.ssossbackend.content.domain.model.GenerationResultStatus;
import com.ssoss.ssossbackend.content.domain.model.LlmCallFailedException;
import com.ssoss.ssossbackend.content.domain.model.LlmCallReply;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GeminiContentGenerator implements ContentGenerator {

    private static final BeanOutputConverter<TitledGenerationOutput> TITLED_CONVERTER =
        new BeanOutputConverter<>(TitledGenerationOutput.class);
    private static final BeanOutputConverter<UntitledGenerationOutput> UNTITLED_CONVERTER =
        new BeanOutputConverter<>(UntitledGenerationOutput.class);
    private static final BeanOutputConverter<PhotoGuidedTitledGenerationOutput> PHOTO_GUIDED_TITLED_CONVERTER =
        new BeanOutputConverter<>(PhotoGuidedTitledGenerationOutput.class);
    private static final BeanOutputConverter<PhotoGuidedUntitledGenerationOutput> PHOTO_GUIDED_UNTITLED_CONVERTER =
        new BeanOutputConverter<>(PhotoGuidedUntitledGenerationOutput.class);
    private static final BeanOutputConverter<BodyOnlyGenerationOutput> BODY_ONLY_CONVERTER =
        new BeanOutputConverter<>(BodyOnlyGenerationOutput.class);
    private static final BeanOutputConverter<PhotoGuidedBodyOnlyGenerationOutput> PHOTO_GUIDED_BODY_ONLY_CONVERTER =
        new BeanOutputConverter<>(PhotoGuidedBodyOnlyGenerationOutput.class);

    private final GoogleGenAiChatModel chatModel;
    private final GenerationPromptComposer promptComposer;
    private final GeminiCallOutcomeClassifier outcomeClassifier;
    private final PhotoGuideAssembler photoGuideAssembler;

    @Override
    public LlmCallReply generate(GenerationMaterial material) {
        BeanOutputConverter<?> converter = switch (material.channel()) {
            case BLOG -> material.photoGuideChecked() ? PHOTO_GUIDED_TITLED_CONVERTER : TITLED_CONVERTER;
            case INSTAGRAM, THREADS ->
                material.photoGuideChecked() ? PHOTO_GUIDED_UNTITLED_CONVERTER : UNTITLED_CONVERTER;
            case DAANGN_BIZ ->
                material.photoGuideChecked() ? PHOTO_GUIDED_BODY_ONLY_CONVERTER : BODY_ONLY_CONVERTER;
        };
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
            .model(chatModel.getOptions().getModel())
            .responseMimeType(MediaType.APPLICATION_JSON_VALUE)
            .responseSchema(converter.getJsonSchema())
            .build();
        PhotoGuidePlacement placement = ChannelPromptPolicy.of(material.channel()).photoGuidePlacement();
        long startedAtNanos = System.nanoTime();
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(promptComposer.compose(material), options));
        } catch (RuntimeException e) {
            throw new LlmCallFailedException(outcomeClassifier.classify(e),
                Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis(), e);
        }
        long responseTimeMillis = Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
        Usage usage = response.getMetadata().getUsage();
        String text = null;
        try {
            text = response.getResult().getOutput().getText();
            GeneratedContent content = switch (converter.convert(text)) {
                case PhotoGuidedTitledGenerationOutput output -> new GeneratedContent(output.title(),
                    photoGuideAssembler.assemble(output.paragraphs(), output.photoGuides(), placement),
                    output.hashtags());
                case PhotoGuidedUntitledGenerationOutput output -> new GeneratedContent(null,
                    photoGuideAssembler.assemble(output.paragraphs(), output.photoGuides(), placement),
                    output.hashtags());
                case TitledGenerationOutput output -> new GeneratedContent(output.title(),
                    photoGuideAssembler.assemble(output.paragraphs(), List.of()), output.hashtags());
                case UntitledGenerationOutput output -> new GeneratedContent(null,
                    photoGuideAssembler.assemble(output.paragraphs(), List.of()), output.hashtags());
                case PhotoGuidedBodyOnlyGenerationOutput output -> new GeneratedContent(null,
                    photoGuideAssembler.assemble(output.paragraphs(), output.photoGuides(), placement), List.of());
                case BodyOnlyGenerationOutput output -> new GeneratedContent(null,
                    photoGuideAssembler.assemble(output.paragraphs(), List.of()), List.of());
                default -> throw new IllegalStateException("알 수 없는 생성 산출 형태입니다");
            };
            return new LlmCallReply(content, responseTimeMillis,
                usage.getPromptTokens(), usage.getCompletionTokens(), text);
        } catch (RuntimeException e) {
            throw new LlmCallFailedException(GenerationResultStatus.EMPTY_OUTPUT, responseTimeMillis,
                usage.getPromptTokens(), usage.getCompletionTokens(), text, e);
        }
    }
}
