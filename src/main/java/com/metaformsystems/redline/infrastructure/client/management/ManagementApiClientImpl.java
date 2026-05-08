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

package com.metaformsystems.redline.infrastructure.client.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaformsystems.redline.application.service.TokenProvider;
import com.metaformsystems.redline.domain.entity.ClientCredentials;
import com.metaformsystems.redline.domain.exception.ObjectNotFoundException;
import com.metaformsystems.redline.domain.repository.ParticipantRepository;
import com.metaformsystems.redline.infrastructure.client.management.dto.Asset;
import com.metaformsystems.redline.infrastructure.client.management.dto.Catalog;
import com.metaformsystems.redline.infrastructure.client.management.dto.CatalogRequest;
import com.metaformsystems.redline.infrastructure.client.management.dto.CelExpression;
import com.metaformsystems.redline.infrastructure.client.management.dto.ContractAgreement;
import com.metaformsystems.redline.infrastructure.client.management.dto.ContractNegotiation;
import com.metaformsystems.redline.infrastructure.client.management.dto.ContractRequest;
import com.metaformsystems.redline.infrastructure.client.management.dto.DataplaneRegistration;
import com.metaformsystems.redline.infrastructure.client.management.dto.NewContractDefinition;
import com.metaformsystems.redline.infrastructure.client.management.dto.NewPolicyDefinition;
import com.metaformsystems.redline.infrastructure.client.management.dto.QuerySpec;
import com.metaformsystems.redline.infrastructure.client.management.dto.TransferProcess;
import com.metaformsystems.redline.infrastructure.client.management.dto.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static com.nimbusds.jose.util.Base64URL.encode;

@Component
public class ManagementApiClientImpl implements ManagementApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ManagementApiClientImpl.class);

    private final WebClient controlPlaneWebClient;
    private final TokenProvider tokenProvider;
    private final ParticipantRepository participantRepository;
    private final ClientCredentials adminCredentials;

    public ManagementApiClientImpl(WebClient controlPlaneWebClient,
                                   TokenProvider tokenProvider,
                                   ParticipantRepository participantRepository,
                                   ObjectMapper objectMapper,
                                   @Value("${controlplane.admin.client-id:admin}") String adminClientId,
                                   @Value("${controlplane.admin.client-secret:edc-v-admin-secret}") String adminClientSecret) {
        this.controlPlaneWebClient = controlPlaneWebClient;
        this.tokenProvider = tokenProvider;
        this.participantRepository = participantRepository;
        this.adminCredentials = new ClientCredentials(adminClientId, adminClientSecret);
    }

    @Override
    public void createAsset(String participantContextId, Asset asset) {
        var token = getToken(participantContextId);

        controlPlaneWebClient.post()
                .uri("/v5beta/participants/%s/assets".formatted(participantContextId))
                .header("Authorization", "Bearer %s".formatted(token))
                .bodyValue(asset)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    @Override
    public List<Map<String, Object>> queryAssets(String participantContextId, QuerySpec query) {
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/assets/request", encode(participantContextId))
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .block();
    }

    @Override
    public void deleteAsset(String participantContextId, String assetId) {
        controlPlaneWebClient.delete()
                .uri("/v5beta/participants/{participantContextId}/assets/{assetId}", encode(participantContextId), assetId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void createPolicy(String participantContextId, NewPolicyDefinition policy) {
        controlPlaneWebClient.post()
                .uri("/v5beta/participants/%s/policydefinitions".formatted(participantContextId))
                .header("Authorization", "Bearer %s".formatted(getToken(participantContextId)))
                .bodyValue(policy)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    @Override
    public List<Map<String, Object>> queryPolicyDefinitions(String participantContextId, QuerySpec query) {
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/policydefinitions/request", encode(participantContextId))
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .block();
    }

    @Override
    public void deletePolicyDefinition(String participantContextId, String policyId) {
        controlPlaneWebClient.delete()
                .uri("/v5beta/participants/{participantContextId}/policydefinitions/{policyId}", encode(participantContextId), policyId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void createContractDefinition(String participantContextId, NewContractDefinition contractDefinition) {
        controlPlaneWebClient.post()
                .uri("/v5beta/participants/%s/contractdefinitions".formatted(participantContextId))
                .header("Authorization", "Bearer %s".formatted(getToken(participantContextId)))
                .bodyValue(contractDefinition)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    @Override
    public List<Map<String, Object>> queryContractDefinitions(String participantContextId, QuerySpec query) {
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/contractdefinitions/request", participantContextId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .block();
    }

    @Override
    public void deleteContractDefinition(String participantContextId, String contractDefinitionId) {
        controlPlaneWebClient.delete()
                .uri("/v5beta/participants/{participantContextId}/contractdefinitions/{id}", participantContextId, contractDefinitionId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public String initiateContractNegotiation(String participantContextId, ContractRequest negotiationRequest) {

        try {
            var json = new ObjectMapper().writeValueAsString(negotiationRequest);
            logger.info("Initiating contract negotiation: {}", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        var response = controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/contractnegotiations", participantContextId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(negotiationRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

        return response != null ? (String) response.get("@id") : null;
    }

    @Override
    public ContractNegotiation getContractNegotiation(String participantContextId, String negotiationId) {
        return controlPlaneWebClient.get()
                .uri("/v5beta/participants/{participantContextId}/contractnegotiations/{id}", participantContextId, negotiationId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ContractNegotiation>() {
                })
                .block();
    }

    @Override
    public List<Map<String, Object>> queryContractNegotiations(String participantContextId, QuerySpec query) {
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/contractnegotiations/request", encode(participantContextId))
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .block();
    }

    @Override
    public void createCelExpression(CelExpression celExpression) {

        var token = tokenProvider.getToken(adminCredentials.clientId(), adminCredentials.clientSecret(), "management-api:write management-api:read");
        controlPlaneWebClient.post()
                .uri("/v5beta/celexpressions")
                .header("Authorization", "Bearer %s".formatted(token))
                .bodyValue(celExpression)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    @Override
    public Map<String, String> setupTransfer(String participantContextId, String policyId, String providerId) {
        return controlPlaneWebClient.post()
                .uri("/v1alpha/participants/%s/transfer".formatted(participantContextId))
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(Map.of(
                        "policyId", policyId,
                        "providerId", providerId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .block();
    }

    @Override
    public List<TransferProcess> listTransferProcesses(String participantContextId) {
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/transferprocesses/request", participantContextId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TransferProcess>>() {
                })
                .block();
    }

    @Override
    public String initiateTransferProcess(String participantContextId, TransferRequest request) {
        var response = controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/transferprocesses", participantContextId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

        return response != null ? response.get("@id").toString() : null;
    }

    @Override
    public TransferProcess getTransferProcess(String participantContextId, String transferProcessId) {
        return controlPlaneWebClient.get()
                .uri("/v5beta/participants/{participantContextId}/transferprocesses/{transferProcessId}", participantContextId, transferProcessId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .bodyToMono(TransferProcess.class)
                .block();
    }

    @Override
    public Catalog getCatalog(String participantContextId, CatalogRequest request) {
        logger.info("Requesting catalog for participantContextId={}, request={}", participantContextId, request.toString());
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/%s/catalog/request".formatted(participantContextId))
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Catalog.class)
                .block();

    }

    @Override
    public void prepareDataplane(String participantContextId, DataplaneRegistration dataplaneRegistration) {
        controlPlaneWebClient.put()
                .uri("/v5beta/participants/%s/dataplanes".formatted(participantContextId))
                .header("Authorization", "Bearer %s".formatted(getToken(participantContextId)))
                .bodyValue(dataplaneRegistration)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    @Override
    public List<ContractNegotiation> listContracts(String participantContextId) {
        return controlPlaneWebClient.post()
                .uri("/v5beta/participants/{participantContextId}/contractnegotiations/request", participantContextId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ContractNegotiation>>() {
                })
                .block();
    }

    @Override
    public ContractAgreement getAgreement(String participantContextId, String negotiationId) {
        return controlPlaneWebClient.get()
                .uri("/v5beta/participants/{participantContextId}/contractnegotiations/{negotiationId}/agreement", participantContextId, negotiationId)
                .header("Authorization", "Bearer " + getToken(participantContextId))
                .retrieve()
                .bodyToMono(ContractAgreement.class)
                .block();
    }

    private String getToken(String participantContextId) {
        var participantProfile = participantRepository.findByParticipantContextId(participantContextId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with context id: " + participantContextId));

        return tokenProvider.getToken(participantProfile.getClientCredentials().clientId(), participantProfile.getClientCredentials().clientSecret(), "management-api:write management-api:read");
    }

}
