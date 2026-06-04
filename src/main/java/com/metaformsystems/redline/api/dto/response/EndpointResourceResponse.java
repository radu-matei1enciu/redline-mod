package com.metaformsystems.redline.api.dto.response;

import java.util.Map;

public record EndpointResourceResponse(
        String assetId,
        String endpointUrl,
        String name,
        Map<String, Object> metadata
) {}
