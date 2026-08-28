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

    private final BedrockRuntimeClient bedrockRuntimeClient;

    @Value("${aws.bedrock.model-id}")
    private String modelId;


    public String converse(
            String prompt
    ) {

        Message message =
                Message.builder()

                        /*
                         * 사용자 메시지
                         */
                        .role(
                                ConversationRole.USER
                        )

                        /*
                         * 실제 Prompt
                         */
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

                                                    /*
                                                     * Nova 2 Lite
                                                     */
                                                    .modelId(
                                                            modelId
                                                    )

                                                    /*
                                                     * 대화 메시지
                                                     */
                                                    .messages(
                                                            message
                                                    )

                                                    /*
                                                     * 생성 옵션
                                                     */
                                                    .inferenceConfig(
                                                            config ->
                                                                    config

                                                                            /*
                                                                             * 최대 출력 Token
                                                                             */
                                                                            .maxTokens(
                                                                                    1000
                                                                            )

                                                                            /*
                                                                             * 낮을수록
                                                                             * 일관적인 답변
                                                                             */
                                                                            .temperature(
                                                                                    0.2F
                                                                            )
                                                    )
                            );


            /*
             * 응답 검증
             */
            if (
                    response.output() == null
                            ||
                            response.output()
                                    .message() == null
                            ||
                            response.output()
                                    .message()
                                    .content() == null
                            ||
                            response.output()
                                    .message()
                                    .content()
                                    .isEmpty()
            ) {

                throw new IllegalStateException(
                        "Bedrock 응답이 비어 있습니다."
                );
            }


            /*
             * 첫 번째 Text Content 반환
             */
            return response
                    .output()
                    .message()
                    .content()
                    .get(0)
                    .text();


        } catch (
                BedrockRuntimeException e
        ) {

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


            if (
                    e.awsErrorDetails()
                            != null
            ) {

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