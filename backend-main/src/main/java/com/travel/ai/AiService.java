package com.travel.ai;

import com.travel.external.bedrock.BedrockClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiService {

    private final BedrockClient bedrockClient;


    /*
     * 현재는 Bedrock 연결 테스트용
     *
     * 나중에
     *
     * recommendFlight()
     * recommendAccommodation()
     * createItinerary()
     *
     * 등으로 확장
     */
    public String test(
            String prompt
    ) {

        return bedrockClient.converse(
                prompt
        );
    }
}