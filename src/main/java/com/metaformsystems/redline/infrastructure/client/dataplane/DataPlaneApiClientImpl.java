/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package com.metaformsystems.redline.infrastructure.client.dataplane;

import com.metaformsystems.redline.application.service.TokenProvider;
import com.metaformsystems.redline.domain.exception.ObjectNotFoundException;
import com.metaformsystems.redline.domain.repository.ParticipantRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class DataPlaneApiClientImpl implements DataPlaneApiClient {

    private final WebClient.Builder webClientBuilder;
    private final ParticipantRepository participantRepository;
    private final TokenProvider tokenProvider;

    public DataPlaneApiClientImpl(
            WebClient.Builder webClientBuilder,
            ParticipantRepository participantRepository,
            TokenProvider tokenProvider
    ) {
        this.webClientBuilder = webClientBuilder;
        this.participantRepository = participantRepository;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public List<Map<String, Object>> getJson(String participantContextId, String endpointUrl) {
        return webClientBuilder.build()
                .get()
                .uri(endpointUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getToken(participantContextId))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .block();
    }

    private String getToken(String participantContextId) {
        var participantProfile = participantRepository.findByParticipantContextId(participantContextId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with context id: " + participantContextId));

        return tokenProvider.getToken(participantProfile.getClientCredentials().clientId(), participantProfile.getClientCredentials().clientSecret(), "management-api:write management-api:read");
    }
}
