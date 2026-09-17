package com.travel.accommodation.batch;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AccommodationRegionParser {

    public Region parse(
            String roadAddress,
            String lotAddress
    ) {
        String address = firstNonBlank(
                roadAddress,
                lotAddress
        );

        if (address == null) {
            return new Region(
                    null,
                    null,
                    null
            );
        }

        String[] tokens =
                address.trim().split("\\s+");

        if (tokens.length == 0) {
            return new Region(
                    null,
                    null,
                    null
            );
        }

        String province = clean(tokens[0]);

        List<String> cityParts =
                new ArrayList<>();

        String town = null;

        for (int i = 1; i < tokens.length; i++) {

            String token = clean(tokens[i]);

            if (token == null || token.isBlank()) {
                continue;
            }

            if (isTown(token)) {
                town = token;
                break;
            }

            if (isCity(token)) {
                cityParts.add(token);
            }
        }

        String city =
                cityParts.isEmpty()
                        ? null
                        : String.join(
                        " ",
                        cityParts
                );

        return new Region(
                province,
                city,
                town
        );
    }


    private boolean isCity(String value) {

        return value.endsWith("시")
                || value.endsWith("군")
                || value.endsWith("구");
    }


    private boolean isTown(String value) {

        return value.endsWith("읍")
                || value.endsWith("면")
                || value.endsWith("동")
                || value.endsWith("가");
    }


    private String clean(String value) {

        if (value == null) {
            return null;
        }

        return value
                .replace("(", "")
                .replace(")", "")
                .replace(",", "")
                .trim();
    }


    private String firstNonBlank(
            String first,
            String second
    ) {

        if (first != null
                && !first.isBlank()) {

            return first;
        }

        if (second != null
                && !second.isBlank()) {

            return second;
        }

        return null;
    }


    public record Region(
            String province,
            String city,
            String town
    ) {
    }
}