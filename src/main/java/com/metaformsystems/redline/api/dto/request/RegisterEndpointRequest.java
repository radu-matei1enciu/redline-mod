package com.metaformsystems.redline.api.dto.request;

import com.metaformsystems.redline.infrastructure.client.management.dto.CelExpression;
import com.metaformsystems.redline.infrastructure.client.management.dto.PolicySet;

import java.util.List;
import java.util.Map;

public record RegisterEndpointRequest(
        String endpointUrl,
        String name,
        Map<String, Object> publicMetadata,
        Map<String, Object> privateMetadata,
        List<CelExpression> celExpressions,
        PolicySet policySet
) {}
