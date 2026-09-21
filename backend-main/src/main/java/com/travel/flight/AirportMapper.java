package com.travel.flight;

import org.springframework.stereotype.Component;

@Component
public class AirportMapper {

    public String resolve(String region) {

        if (
                region == null
                        || region.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "지역 정보가 없습니다."
            );
        }


        /*
         * 제주
         */
        if (
                region.contains("제주")
        ) {

            return "CJU";
        }


        /*
         * 서울 / 경기 / 인천
         *
         * 국내선 기준 김포공항
         */
        if (
                region.contains("서울")
                        || region.contains("경기")
                        || region.contains("인천")
        ) {

            return "GMP";
        }


        /*
         * 부산 / 김해 / 창원
         */
        if (
                region.contains("부산")
                        || region.contains("김해")
                        || region.contains("창원")
        ) {

            return "PUS";
        }


        /*
         * 대구
         */
        if (
                region.contains("대구")
        ) {

            return "TAE";
        }


        /*
         * 울산
         */
        if (
                region.contains("울산")
        ) {

            return "USN";
        }


        /*
         * 광주
         */
        if (
                region.contains("광주")
        ) {

            return "KWJ";
        }


        /*
         * 여수 / 순천
         */
        if (
                region.contains("여수")
                        || region.contains("순천")
        ) {

            return "RSU";
        }


        /*
         * 진주 / 사천
         */
        if (
                region.contains("진주")
                        || region.contains("사천")
        ) {

            return "HIN";
        }


        /*
         * 포항 / 경주
         */
        if (
                region.contains("포항")
                        || region.contains("경주")
        ) {

            return "KPO";
        }


        /*
         * 충북 / 청주
         */
        if (
                region.contains("충청북도")
                        || region.contains("충북")
                        || region.contains("청주")
        ) {

            return "CJJ";
        }


        /*
         * 대전 / 세종
         *
         * MVP에서는 청주공항 연결
         */
        if (
                region.contains("대전")
                        || region.contains("세종")
        ) {

            return "CJJ";
        }


        /*
         * 군산
         */
        if (
                region.contains("군산")
        ) {

            return "KUV";
        }


        /*
         * 강원 영동권
         */
        if (
                region.contains("양양")
                        || region.contains("속초")
                        || region.contains("강릉")
        ) {

            return "YNY";
        }


        /*
         * 원주
         */
        if (
                region.contains("원주")
        ) {

            return "WJU";
        }


        throw new IllegalArgumentException(
                "항공편 조회를 지원하지 않는 지역입니다: "
                        + region
        );
    }
}