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
import com.metaformsystems.redline.api.dto.response.FileResource;
import com.metaformsystems.redline.domain.entity.DataspaceInfo;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
        this.catalogCache = new ConcurrentLruCache<>(100, key -> fetchCatalog(key.participantId(), key.did()));
        this.webDidResolver = webDidResolver;
    }

    @Transactional
    public void uploadFileForParticipant(Long participantId, Map<String, Object> publicMetadata, Map<String, Object> privateMetadata, InputStream fileStream, String contentType, String originalFilename, List<CelExpression> celExpressions, PolicySet policySet) {

        var participant = participantRepository.findById(participantId).orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var participantContextId = participant.getParticipantContextId();

        // Resolve credential type dynamically from the participant's dataspace
        var credentialType = resolveCredentialType(participant);

        //0. upload file to data plane
        var assetId = UUID.randomUUID().toString();
        publicMetadata.put("assetId", assetId);
        var combinedMetadata = Stream.of(publicMetadata, privateMetadata).flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var response = dataPlaneApiClient.uploadMultipart(participantContextId, combinedMetadata, fileStream);
        var fileId = response.id();

        //1. create CEL expressions
        var expressions = new ArrayList<>(celExpressions);
        expressions.add(CelExpression.Builder.aNewCelExpression()
                .id(MEMBERSHIP_EXPRESSION_ID)
                .leftOperand("CatenaMembershipCredential")
                .description("Expression for evaluating membership credential")
                .scopes(Set.of("catalog", "contract.negotiation", "transfer.process"))
                .expression(MEMBERSHIP_EXPRESSION)
                .build());
        expressions.forEach(celExpression -> {
            try {
                managementApiClient.createCelExpression(celExpression);
            } catch (WebClientResponseException.Conflict e) {
                //do nothing, CEL expression already exists
            }
        });

        //2. create asset
        publicMetadata.put("fileId", fileId);
        var asset = createAsset(assetId, publicMetadata, privateMetadata, contentType, originalFilename);
        managementApiClient.createAsset(participantContextId, asset);

        //3. create policy
        if (policySet != null) {
            var constraints = new ArrayList<>(List.of(MEMBERSHIP_CONSTRAINT));
            constraints.addAll(policySet.getPermission().getFirst().getConstraint());
            policySet.getPermission().getFirst().setConstraint(constraints);
        } else {
            policySet = new PolicySet(List.of(new PolicySet.Permission("use",
                    new ArrayList<>(List.of(MEMBERSHIP_CONSTRAINT))
            )));
        }
        var policy = NewPolicyDefinition.Builder.aNewPolicyDefinition()
                .id(UUID.randomUUID().toString())
                .policy(policySet).build();
        managementApiClient.createPolicy(participantContextId, policy);

        //4. create contract definition if none exists
        var contractDef = NewContractDefinition.Builder.aNewContractDefinition()
                .id(UUID.randomUUID().toString())
                .contractPolicyId(policy.getId())
                .accessPolicyId(policy.getId())
                .assetsSelector(Set.of(new Criterion("id", "=", assetId)))
                .build();
        managementApiClient.createContractDefinition(participantContextId, contractDef);

        //5. track uploaded file in DB
        participant.getUploadedFiles().add(new UploadedFile(fileId, originalFilename, contentType, combinedMetadata));
    }

    @Transactional
    public List<FileResource> listFilesForParticipant(Long participantId) {
        var participant = participantRepository.findById(participantId).orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        return participant.getUploadedFiles().stream()
                .map(f -> new FileResource(f.getFileId(), f.getOriginalFilename(), f.getContentType(), f.getCreatedAt().toString(), f.getMetadata()))
                .toList();
    }

    @Transactional
    public Catalog requestCatalog(Long participantId, String counterPartyIdentifier, String cacheControl) {

        var participant = participantRepository.findById(participantId).orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));

        var key = new LookupKey(participant.getParticipantContextId(), counterPartyIdentifier);
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

    @Transactional
    public byte[] downloadData(Long participantId, String fileId, String authToken) {
        participantRepository.findById(participantId).orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        return dataPlaneApiClient.downloadFile(authToken, fileId);
    }

    /**
     * Resolves the membership credential type for a participant based on their dataspace's
     * 'credentialType' property. Falls back to CatenaMembershipCredential if not configured.
     */
    private String resolveCredentialType(com.metaformsystems.redline.domain.entity.Participant participant) {
        return participant.getDataspaceInfos().stream()
                .map(DataspaceInfo::getDataspaceId)
                .flatMap(id -> dataspaceRepository.findById(id).stream())
                .map(ds -> ds.getProperties().get("credentialType"))
                .filter(v -> v instanceof String s && StringUtils.hasText(s))
                .map(Object::toString)
                .findFirst()
                .orElseGet(() -> {
                    log.warn("No credentialType property found for participant {}, defaulting to CatenaMembershipCredential",
                            participant.getId());
                    return "CatenaMembershipCredential";
                });
    }

    private CacheableEntry<Catalog> fetchCatalog(String participantId, String did) {
        var counterPartyAddress = webDidResolver.resolveProtocolEndpoints(did);

        // Include the consumer's specific membership credential type as an additional scope
        // so the provider requests it from IdentityHub during catalog evaluation
        var participant = participantRepository.findByParticipantContextId(participantId)
                .orElse(null);
        var additionalScopes = new ArrayList<String>();
        if (participant != null) {
            var credentialType = resolveCredentialType(participant);
            additionalScopes.add("org.eclipse.dspace.dcp.vc.type:" + credentialType + ":read");
        }

        var request = CatalogRequest.Builder.newInstance()
                .counterPartyId(did)
                .counterPartyAddress(counterPartyAddress)
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

    private Asset createAsset(String id, Map<String, Object> publicMetadata, Map<String, Object> privateMetadata, String contentType, String originalFilename) {

        var properties = new HashMap<String, Object>(Map.of(
                "description", "A file uploaded by Redline on " + Instant.now().toString(),
                "contentType", contentType,
                "originalFilename", originalFilename));
        properties.putAll(publicMetadata);

        privateMetadata.put("permission", ASSET_PERMISSION);

        return Asset.Builder.aNewAsset()
                .id(id)
                .dataAddress(Map.of(
                        "type", "HttpCertData",
                        "@type", "DataAddress"
                ))
                .privateProperties(privateMetadata) //this is targeted by the CEL expression, so it must be a private property
                .properties(Map.of("properties", properties))
                .build();
    }

    private record CacheableEntry<T>(T value, Instant timestamp) {
    }

    private record LookupKey(String participantId, String did) {
    }
}
