package com.travel.global;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상태 확인", description = "서버의 상태가 정상적인지 확인합니다.")
@RestController
public class HealthController {
    @Operation(summary = "서버 상태 확인", description = "서버의 상태가 정상적인지 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "서버 상태가 정상입니다."),
            @ApiResponse(responseCode = "500", description = "서버 상태가 비정상입니다.")
    })
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}