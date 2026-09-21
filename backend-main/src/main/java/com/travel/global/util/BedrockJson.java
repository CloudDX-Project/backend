package com.travel.global.util;

/** Bedrock 텍스트 응답에서 첫 JSON 객체 범위를 추출한다. */
public final class BedrockJson {
    private BedrockJson() {
    }

    public static String extractObject(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Bedrock 응답이 없습니다.");
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("Bedrock 응답에서 JSON을 찾을 수 없습니다.");
        }
        return trimmed.substring(start, end + 1);
    }
}
