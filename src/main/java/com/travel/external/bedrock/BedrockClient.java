package com.travel.external.bedrock;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@Component
@RequiredArgsConstructor
public class BedrockClient {

    private static final int DEFAULT_MAX_TOKENS =
            1000;

    private static final float DEFAULT_TEMPERATURE =
            0.2F;

    private final BedrockRuntimeClient bedrockRuntimeClient;

    @Value("${aws.bedrock.model-id}")
    private String modelId;

    /*
     * 기존 관광지 / 식당 / 카페 추천 로직은
     * 이 메서드를 그대로 사용한다.
     */
    public String converse(
            String prompt
    ) {

        return converse(
                prompt,
                DEFAULT_MAX_TOKENS,
                DEFAULT_TEMPERATURE
        );
    }

    /*
     * 최종 여행일정 Planner처럼
     * 출력 JSON이 긴 경우 maxTokens와 temperature를
     * 호출부에서 별도로 지정할 수 있게 추가한다.
     */
    public String converse(
            String prompt,
            int maxTokens,
            float temperature
    ) {

        Message message =
                Message.builder()
                        .role(
                                ConversationRole.USER
                        )
                        .content(
                                ContentBlock.fromText(
                                        prompt
                                )
                        )
                        .build();

        try {

            ConverseResponse response =
                    bedrockRuntimeClient
                            .converse(
                                    request ->
                                            request
                                                    .modelId(
                                                            modelId
                                                    )
                                                    .messages(
                                                            message
                                                    )
                                                    .inferenceConfig(
                                                            config ->
                                                                    config
                                                                            .maxTokens(
                                                                                    maxTokens
                                                                            )
                                                                            .temperature(
                                                                                    temperature
                                                                            )
                                                    )
                            );

            if (
                    response.output() == null
                            || response.output()
                            .message() == null
                            || response.output()
                            .message()
                            .content() == null
                            || response.output()
                            .message()
                            .content()
                            .isEmpty()
            ) {

                throw new IllegalStateException(
                        "Bedrock 응답이 비어 있습니다."
                );
            }

            return response
                    .output()
                    .message()
                    .content()
                    .get(0)
                    .text();

        } catch (BedrockRuntimeException e) {

            System.out.println(
                    "===== BEDROCK API ERROR ====="
            );

            System.out.println(
                    "MODEL = " + modelId
            );

            System.out.println(
                    "STATUS = "
                            + e.statusCode()
            );

            if (e.awsErrorDetails() != null) {

                System.out.println(
                        "ERROR CODE = "
                                + e.awsErrorDetails()
                                .errorCode()
                );

                System.out.println(
                        "ERROR MESSAGE = "
                                + e.awsErrorDetails()
                                .errorMessage()
                );
            }

            System.out.println(
                    "============================="
            );

            throw new IllegalStateException(
                    "Bedrock 호출 중 오류가 발생했습니다.",
                    e
            );
        }
    }
}