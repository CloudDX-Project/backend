package com.travel.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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