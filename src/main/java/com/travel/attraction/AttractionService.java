package com.travel.attraction;

import com.travel.attraction.data.TouristAttractionData;
import com.travel.attraction.dto.AttractionCandidate;
import com.travel.attraction.dto.AttractionSearchRequest;
import com.travel.attraction.dto.AttractionSearchResponse;
import com.travel.attraction.repository.TouristAttractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AttractionService {

    private final TouristAttractionRepository attractionRepository;

    public AttractionService(
            TouristAttractionRepository attractionRepository
    ) {
        this.attractionRepository = attractionRepository;
    }

    @Transactional(readOnly = true)
    public AttractionSearchResponse search(
            AttractionSearchRequest request
    ) {

        List<TouristAttractionData> data =
                attractionRepository.search(
                        request.region1Name(),
                        request.region2Name(),
                        request.keyword(),
                        request.resolvedLimit()
                );

        List<AttractionCandidate> attractions =
                data.stream()
                        .map(this::toCandidate)
                        .toList();

        return new AttractionSearchResponse(
                request.region1Name(),
                request.region2Name(),
                request.keyword(),
                attractions.size(),
                attractions
        );
    }

    private AttractionCandidate toCandidate(
            TouristAttractionData attraction
    ) {

        return new AttractionCandidate(

                attraction.id(),

                attraction.provider(),
                attraction.providerId(),

                attraction.name(),

                attraction.categoryCode(),
                attraction.categoryName(),

                attraction.region1Code(),
                attraction.region1Name(),

                attraction.region2Code(),
                attraction.region2Name(),

                attraction.address(),
                attraction.roadAddress(),
                attraction.postcode(),

                attraction.latitude(),
                attraction.longitude(),

                attraction.tags(),
                attraction.allTags(),

                attraction.introduction(),
                attraction.phoneNumber(),

                attraction.photoId(),
                attraction.representativeImageUrl(),
                attraction.thumbnailImageUrl()
        );
    }
}