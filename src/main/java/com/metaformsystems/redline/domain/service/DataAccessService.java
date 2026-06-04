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
 *       Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V. - CEL and policy creation for uploaded file
 *
 */

package com.metaformsystems.redline.domain.service;

import com.metaformsystems.redline.api.dto.request.TransferProcessRequest;
import com.metaformsystems.redline.api.dto.response.EndpointResourceResponse;
import com.metaformsystems.redline.api.dto.response.FileResource;
import com.metaformsystems.redline.domain.entity.UploadedFile;
import com.metaformsystems.redline.domain.exception.ObjectNotFoundException;
import com.metaformsystems.redline.domain.repository.DataspaceRepository;
import com.metaformsystems.redline.domain.repository.ParticipantRepository;
import com.metaformsystems.redline.infrastructure.client.dataplane.DataPlaneApiClient;
import com.metaformsystems.redline.infrastructure.client.management.ManagementApiClient;
import com.metaformsystems.redline.infrastructure.client.management.dto.Asset;
import com.metaformsystems.redline.infrastructure.client.management.dto.Catalog;
import com.metaformsystems.redline.infrastructure.client.management.dto.CatalogRequest;
import com.metaformsystems.redline.infrastructure.client.management.dto.CelExpression;
import com.metaformsystems.redline.infrastructure.client.management.dto.ContractNegotiation;
import com.metaformsystems.redline.infrastructure.client.management.dto.ContractRequest;
import com.metaformsystems.redline.infrastructure.client.management.dto.Criterion;
import com.metaformsystems.redline.infrastructure.client.management.dto.NewContractDefinition;
import com.metaformsystems.redline.infrastructure.client.management.dto.NewPolicyDefinition;
import com.metaformsystems.redline.infrastructure.client.management.dto.PolicySet;
import com.metaformsystems.redline.infrastructure.client.management.dto.TransferProcess;
import com.metaformsystems.redline.infrastructure.client.management.dto.TransferRequest;
import com.metaformsystems.redline.infrastructure.client.siglet.SigletApiClient;
import com.metaformsystems.redline.domain.entity.EndpointResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.metaformsystems.redline.domain.service.Constants.*;

@Service
public class DataAccessService {
    private static final Logger log = LoggerFactory.getLogger(DataAccessService.class);
    private final DataPlaneApiClient dataPlaneApiClient;
    private final ConcurrentLruCache<LookupKey, CacheableEntry<Catalog>> catalogCache;
    private final WebDidResolver webDidResolver;
    private final ParticipantRepository participantRepository;
    private final DataspaceRepository dataspaceRepository;
    private final ManagementApiClient managementApiClient;
    private final SigletApiClient sigletApiClient;

    public DataAccessService(DataPlaneApiClient dataPlaneApiClient,
                             WebDidResolver webDidResolver,
                             ParticipantRepository participantRepository,
                             DataspaceRepository dataspaceRepository,
                             ManagementApiClient managementApiClient,
                             SigletApiClient sigletApiClient) {
        this.dataPlaneApiClient = dataPlaneApiClient;
        this.participantRepository = participantRepository;
        this.dataspaceRepository = dataspaceRepository;
        this.managementApiClient = managementApiClient;
        this.sigletApiClient = sigletApiClient;
        this.catalogCache = new ConcurrentLruCache<>(100, key -> fetchCatalog(key.participantId(), key.did(), key.additionalScopes()));
        this.webDidResolver = webDidResolver;
    }

    @Transactional
    public List<Map<String, Object>> getAssetData(Long participantId, String assetId) {
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));

        // Try the requesting participant's own endpoints first (provider calling their own asset)
        var localMatch = participant.getEndpointResources().stream()
                .filter(r -> assetId.equals(r.getAssetId()))
                .findFirst();

        if (localMatch.isPresent()) {
            return dataPlaneApiClient.getJson(
                    participant.getParticipantContextId(),
                    localMatch.get().getEndpointUrl()
            );
        }

        // Not found locally — consumer use case: search all participants for the asset owner
        var ownerAndEndpoint = participantRepository.findAll().stream()
                .flatMap(p -> p.getEndpointResources().stream()
                        .filter(r -> assetId.equals(r.getAssetId()))
                        .map(r -> Map.entry(p, r)))
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException("Endpoint asset not found with id: " + assetId));

        return dataPlaneApiClient.getJson(
                ownerAndEndpoint.getKey().getParticipantContextId(),
                ownerAndEndpoint.getValue().getEndpointUrl()
        );
    }

    @Transactional
    public List<EndpointResourceResponse> listEndpointsForParticipant(Long participantId) {
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));

        return participant.getEndpointResources().stream()
                .map(endpoint -> new EndpointResourceResponse(
                        endpoint.getAssetId(),
                        endpoint.getEndpointUrl(),
                        endpoint.getName(),
                        endpoint.getMetadata()
                ))
                .toList();
    }

    @Transactional
    public void registerEndpointForParticipant(Long participantId,
                                            String endpointUrl,
                                            String name,
                                            Map<String, Object> publicMetadata,
                                            Map<String, Object> privateMetadata,
                                            List<CelExpression> celExpressions,
                                            PolicySet policySet,
                                            Long dataspaceId) {

        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var participantContextId = participant.getParticipantContextId();

        var credentialType = dataspaceRepository.findDataspacesByParticipantId(participantId)
                .stream()
                .filter(d -> Objects.equals(d.getId(), dataspaceId))
                .map(d -> (String) d.getProperties().get("credentialType"))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException("Credential type not found"));

        var membershipExpression = MEMBERSHIP_EXPRESSION_TEMPLATE.formatted(credentialType);
        var membershipConstraint = new PolicySet.Constraint(
                credentialType,
                MEMBERSHIP_CONSTRAINT_OPERATOR,
                MEMBERSHIP_CONSTRAINT_RIGHT_OPERAND
        );

        log.info("Registering endpoint for participant {} in dataspace {} — credential type: {}",
                participantId, dataspaceId, credentialType);

        var assetId = UUID.randomUUID().toString();
        publicMetadata.put("assetId", assetId);

        var combinedMetadata = Stream.of(publicMetadata, privateMetadata)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 1. create CEL expressions
        var expressions = new ArrayList<>(celExpressions);
        expressions.add(CelExpression.Builder.aNewCelExpression()
                .id(MEMBERSHIP_EXPRESSION_ID_TEMPLATE.formatted(credentialType.toLowerCase()))
                .leftOperand(credentialType)
                .description("Expression for evaluating membership credential")
                .scopes(Set.of("catalog", "contract.negotiation", "transfer.process"))
                .expression(membershipExpression)
                .build());
        expressions.forEach(celExpression -> {
            try {
                managementApiClient.createCelExpression(celExpression);
            } catch (WebClientResponseException.Conflict e) {
                // already exists, nothing to do
            }
        });

        // 2. create asset with HttpData data address pointing to the endpoint URL
        var asset = createEndpointAsset(assetId, endpointUrl, name, publicMetadata, privateMetadata);
        managementApiClient.createAsset(participantContextId, asset);

        // 3. create policy
        if (policySet != null) {
            var constraints = new ArrayList<>(List.of(membershipConstraint));
            constraints.addAll(policySet.getPermission().getFirst().getConstraint());
            policySet.getPermission().getFirst().setConstraint(constraints);
        } else {
            policySet = new PolicySet(List.of(new PolicySet.Permission("use",
                    new ArrayList<>(List.of(membershipConstraint))
            )));
        }
        var policy = NewPolicyDefinition.Builder.aNewPolicyDefinition()
                .id(UUID.randomUUID().toString())
                .policy(policySet).build();
        managementApiClient.createPolicy(participantContextId, policy);

        // 4. create contract definition
        var contractDef = NewContractDefinition.Builder.aNewContractDefinition()
                .id(UUID.randomUUID().toString())
                .contractPolicyId(policy.getId())
                .accessPolicyId(policy.getId())
                .assetsSelector(Set.of(new Criterion("id", "=", assetId)))
                .build();
        managementApiClient.createContractDefinition(participantContextId, contractDef);

        // 5. track the registered endpoint in the DB
        participant.getEndpointResources().add(
                new EndpointResource(assetId, endpointUrl, name, combinedMetadata)
        );
    }

    @Transactional
    public Catalog requestCatalog(Long participantId, String counterPartyIdentifier, String cacheControl) {

        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));

        var additionalScopes = dataspaceRepository.findDataspacesByParticipantId(participantId)
                .stream()
                .map(d -> (String) d.getProperties().get("credentialType"))
                .filter(Objects::nonNull)
                .map(DCP_SCOPE_TEMPLATE::formatted)
                .toList();

        // credentialTypes is now available alongside participant
        log.info("Credential types for participant {}: {}", participantId, additionalScopes);

        var key = new LookupKey(participant.getParticipantContextId(), counterPartyIdentifier, additionalScopes.stream().sorted().toList());
        var catalogEntry = catalogCache.get(key);
        //todo: check if expired or must be reloaded
        if (isExpired(catalogEntry, cacheControl)) {
            log.info("Catalog cache expired or no-cache requested for participant {} and counterparty {}", participantId, counterPartyIdentifier);

            // removing and re-getting forces a cache update, i.e., reading the remote catalog again
            catalogCache.remove(key);
            return catalogCache.get(key).value();
        }

        return catalogEntry.value();
    }

    @Transactional
    public List<TransferProcess> listTransferProcesses(Long participantId) {
        var participant = participantRepository.findById(participantId).orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var participantContextId = participant.getParticipantContextId();
        return managementApiClient.listTransferProcesses(participantContextId);
    }

    @Transactional
    public List<ContractNegotiation> listContracts(Long participantId) {
        var participant = participantRepository.findById(participantId).orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var participantContextId = participant.getParticipantContextId();

        var negotiations = managementApiClient.listContracts(participantContextId);

        return negotiations.stream().map(cn -> getAgreement(participantContextId, cn))
                .toList();
    }

    @Transactional
    public String initiateContractNegotiation(Long providerId, ContractRequest request) {
        var participant = participantRepository.findById(providerId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + providerId));

        if (request.getCounterPartyAddress() == null) {
            log.info("Counter party address not provided, resolving from DID: {}", request.getProviderId());
            var did = request.getProviderId();
            var addressFromDid = webDidResolver.resolveProtocolEndpoints(did);
            if (addressFromDid == null) {
                throw new IllegalArgumentException("Could not resolve protocol endpoint from DID: " + did);
            }
            request.setCounterPartyAddress(addressFromDid);
        }

        return managementApiClient.initiateContractNegotiation(participant.getParticipantContextId(), request);
    }

    @Transactional
    public ContractNegotiation getContractNegotiation(Long participantId, String contractId) {
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        return managementApiClient.getContractNegotiation(participant.getParticipantContextId(), contractId);
    }

    public String initiateTransferProcess(Long providerId, TransferProcessRequest transferRequest) {
        var participantContextId = getContextId(providerId);

        var address = webDidResolver.resolveProtocolEndpoints(transferRequest.getCounterPartyId());
        if (address == null) {
            throw new ObjectNotFoundException("Could not resolve protocol endpoint from DID: " + transferRequest.getCounterPartyId());
        }

        var rq = TransferRequest.Builder.aTransferRequest()
                .counterPartyAddress(address)
                .transferType(transferRequest.getTransferType())
                .contractId(transferRequest.getContractId())
                .dataDestination(transferRequest.getDataDestination())
                .build();

        return managementApiClient.initiateTransferProcess(participantContextId, rq);
    }

    @Transactional
    public TransferProcess getTransferProcess(Long participantId, String transferProcessId) {
        var contextId = getContextId(participantId);
        var tp = managementApiClient.getTransferProcess(contextId, transferProcessId);
        if ("STARTED".equals(tp.getState())) { //download EDR as well
            // TODO shim layer for old EDR format
            var edr = sigletApiClient.getDataAddress(contextId, transferProcessId);
            tp.setContentDataAddress(Map.of(
                    "properties", Map.of("https://w3id.org/edc/v0.0.1/ns/authorization", edr.get("token"))
            ));
        }
        return tp;
    }

    private CacheableEntry<Catalog> fetchCatalog(String participantId, String did, List<String> additionalScopes) {
        var counterPartyAddress = webDidResolver.resolveProtocolEndpoints(did);
        var request = CatalogRequest.Builder.newInstance()
                .counterPartyId(did)
                .counterPartyAddress(counterPartyAddress)
                .additionalScopes(additionalScopes)
                .build();

        return new CacheableEntry<>(managementApiClient.getCatalog(participantId, request), Instant.now());
    }

    /**
     * Determines if cache entry requires refresh according to the cacheControl value
     */
    private boolean isExpired(CacheableEntry<Catalog> entry, String cacheControl) {
        if (entry == null) return true;
        if (!StringUtils.hasText(cacheControl)) return false;

        if (cacheControl.contains("no-cache") || cacheControl.contains("no-store")) {
            return true;
        }

        // Parse max-age
        var maxAgeMatch = Pattern.compile("max-age=(\\d+)").matcher(cacheControl);
        if (maxAgeMatch.find()) {
            long maxAgeSeconds = Long.parseLong(maxAgeMatch.group(1));
            return entry.timestamp().plus(Duration.ofSeconds(maxAgeSeconds)).isBefore(Instant.now());
        }

        return false;
    }

    private String getContextId(Long providerId) {
        var participant = participantRepository.findById(providerId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + providerId));
        return participant.getParticipantContextId();
    }

    private ContractNegotiation getAgreement(String participantContextId, ContractNegotiation negotiation) {
        if (negotiation.getContractAgreementId() != null) {
            var agreement = managementApiClient.getAgreement(participantContextId, negotiation.getId());
            negotiation.setContractAgreement(agreement);
        }
        return negotiation;
    }

    private Asset createEndpointAsset(String id, String endpointUrl, String name,
                                    Map<String, Object> publicMetadata,
                                    Map<String, Object> privateMetadata) {

        var properties = new HashMap<String, Object>(Map.of(
                "description", "An endpoint registered by Redline on " + Instant.now().toString(),
                "name", name,
                "endpointUrl", endpointUrl));
        properties.putAll(publicMetadata);

        privateMetadata.put("permission", ASSET_PERMISSION);

        return Asset.Builder.aNewAsset()
                .id(id)
                .dataAddress(Map.of(
                        "type", "HttpData",
                        "@type", "DataAddress",
                        "baseUrl", endpointUrl
                ))
                .privateProperties(privateMetadata)
                .properties(Map.of("properties", properties))
                .build();
    }

    private record CacheableEntry<T>(T value, Instant timestamp) {
    }

    private record LookupKey(String participantId, String did, List<String> additionalScopes) {
    }
}
