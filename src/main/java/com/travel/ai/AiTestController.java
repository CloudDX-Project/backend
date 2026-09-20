package com.travel.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI 연결 확인", description = "Amazon Bedrock 모델 연결 상태를 점검하는 개발·운영 확인용 API입니다.")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiTestController {

    private final AiService aiService;


    /*
     * POST
     * /api/ai/test
     */
    @PostMapping("/test")
    @Operation(summary = "AI 응답 테스트", description = "입력한 프롬프트를 Bedrock에 전달하고 모델 응답을 반환합니다. 운영 환경에서는 Worker 권한으로 호출해야 합니다.")
    public AiTestResponse test(

            @Valid
            @RequestBody
            AiTestRequest request
    ) {

        String result =
                aiService.test(
                        request.prompt()
                );


        return new AiTestResponse(
                result
        );
    }


    /*
     * 테스트 Request
     */
    public record AiTestRequest(

            @NotBlank
            String prompt

    ) {
    }


    /*
     * 테스트 Response
     */
    public record AiTestResponse(

            String response

    ) {
    }
}
