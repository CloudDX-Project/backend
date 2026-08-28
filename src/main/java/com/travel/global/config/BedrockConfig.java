package com.travel.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class BedrockConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${aws.bedrock.region}")
            String region
    ) {

        return BedrockRuntimeClient
                .builder()

                /*
                 * AWS 인증정보 자동 탐색
                 *
                 * 환경변수
                 * AWS CLI Profile
                 * SSO
                 * IAM Role
                 * 등을 자동으로 확인
                 */
                .credentialsProvider(
                        DefaultCredentialsProvider.create()
                )

                /*
                 * Bedrock 호출 Region
                 */
                .region(
                        Region.of(region)
                )

                .build();
    }
}