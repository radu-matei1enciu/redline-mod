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

package com.metaformsystems.redline.domain.service;

import com.metaformsystems.redline.api.dto.request.DataPlaneRegistrationRequest;
import com.metaformsystems.redline.api.dto.request.ParticipantDeployment;
import com.metaformsystems.redline.api.dto.request.PartnerReferenceRequest;
import com.metaformsystems.redline.api.dto.request.TenantRegistration;
import com.metaformsystems.redline.api.dto.response.DataspaceResponse;
import com.metaformsystems.redline.api.dto.response.Participant;
import com.metaformsystems.redline.api.dto.response.PartnerReference;
import com.metaformsystems.redline.api.dto.response.Tenant;
import com.metaformsystems.redline.api.dto.response.VirtualParticipantAgent;
import com.metaformsystems.redline.domain.entity.ClientCredentials;
import com.metaformsystems.redline.domain.entity.DataspaceInfo;
import com.metaformsystems.redline.domain.entity.DeploymentState;
import com.metaformsystems.redline.domain.exception.ObjectNotFoundException;
import com.metaformsystems.redline.domain.repository.DataspaceRepository;
import com.metaformsystems.redline.domain.repository.ParticipantRepository;
import com.metaformsystems.redline.domain.repository.ServiceProviderRepository;
import com.metaformsystems.redline.domain.repository.TenantRepository;
import com.metaformsystems.redline.infrastructure.client.hashicorpvault.HashicorpVaultClient;
import com.metaformsystems.redline.infrastructure.client.management.ManagementApiClient;
import com.metaformsystems.redline.infrastructure.client.management.dto.DataplaneRegistration;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.TenantManagerClient;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.ParticipantProfile;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.TenantCreationRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;

/**
 *
 */
@Service
public class TenantService {
    public static final String STATE_PROPERTY_KEY = "cfm.vpa.state";

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private final TenantRepository tenantRepository;
    private final ParticipantRepository participantRepository;
    private final ServiceProviderRepository serviceProviderRepository;
    private final DataspaceRepository dataspaceRepository;
    private final TenantManagerClient tenantManagerClient;
    private final HashicorpVaultClient vaultClient;
    private final ManagementApiClient managementApiClient;


    public TenantService(TenantRepository tenantRepository,
                         ParticipantRepository participantRepository,
                         ServiceProviderRepository serviceProviderRepository,
                         DataspaceRepository dataspaceRepository,
                         TenantManagerClient tenantManagerClient,
                         HashicorpVaultClient vaultClient, ManagementApiClient managementApiClient) {
        this.tenantRepository = tenantRepository;
        this.participantRepository = participantRepository;
        this.serviceProviderRepository = serviceProviderRepository;
        this.dataspaceRepository = dataspaceRepository;
        this.tenantManagerClient = tenantManagerClient;
        this.vaultClient = vaultClient;
        this.managementApiClient = managementApiClient;
    }

    @Transactional
    public List<Tenant> getTenants(Long serviceProviderId) {
        return tenantRepository.findByServiceProviderId(serviceProviderId).stream()
                .map(this::toTenantResource)
                .collect(Collectors.toList());
    }

    @Transactional
    public Tenant getTenant(Long id) {
        return tenantRepository.findById(id)
                .map(this::toTenantResource)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + id));
    }

    @Transactional
    public Tenant registerTenant(Long serviceProviderId, TenantRegistration registration) {
        // Create tenant
        var tenant = new com.metaformsystems.redline.domain.entity.Tenant();
        tenant.setName(registration.tenantName());
        tenant.setServiceProvider(serviceProviderRepository.getReferenceById(serviceProviderId));
        tenant.setProperties(registration.properties());
        // Create participant with dataspaces
        var participant = new com.metaformsystems.redline.domain.entity.Participant();
        participant.setIdentifier(registration.tenantName());
        participant.setTenant(tenant);

        // FIXME for now, register in all dataspaces. This should be changed to support tenant registration in specific dataspaces.
        var dataspaces = registration.dataspaceInfos().stream().map(i -> {
            var info = new DataspaceInfo();
            info.setRoles(i.getRoles());
            info.setAgreementTypes(i.getAgreementTypes());
            info.setDataspaceId(i.getDataspaceId());
            return info;
        }).collect(toSet());

        participant.setDataspaceInfos(dataspaces);

        var savedTenant = tenantRepository.save(tenant);

        participantRepository.save(participant);

        savedTenant.getParticipants().add(participant);

        return toTenantResource(savedTenant);
    }

    /**
     * Adds a new dataspace to an existing participant
     */
    @Transactional
    public Participant joinAdditionalDataspace(Long tenantId, Long participantId,  Long dataspaceId, List<String> roles) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ObjectNotFoundException("Tenant not found with id: " + tenantId));

        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));

        dataspaceRepository.findById(dataspaceId)
                .orElseThrow(() -> new ObjectNotFoundException("Dataspace not found with id: " + dataspaceId));

        // Guard: don't allow duplicate membership.
        boolean alreadyJoined = participant.getDataspaceInfos().stream().anyMatch(ds -> dataspaceId.equals(ds.getDataspaceId()));
        if (alreadyJoined) {
            throw new IllegalStateException(
                    "Tenant " + tenantId + " already participates in dataspace " + dataspaceId);
        }

        var info = new DataspaceInfo();
        info.setDataspaceId(dataspaceId);
        info.setRoles(roles != null ? roles : java.util.List.of());
        info.setAgreementTypes(java.util.List.of());
        participant.addDataspaceInfo(info);

        participantRepository.save(participant);

        return toParticipantResource(participant);
    }

    @Transactional
    public Participant deployParticipant(ParticipantDeployment deployment) {
        var participant = participantRepository.findById(deployment.participantId())
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + deployment.participantId()));

        var tenant = participant.getTenant();
        if (tenant.getCorrelationId() == null) {
            // Create Tenant in CFM and update tenant with correlation id
            var tmTenant = tenantManagerClient.createTenant(new TenantCreationRequest(Map.of("name", tenant.getName())));
            tenant.setCorrelationId(tmTenant.id());
        }

        // invoke CFM to deploy the ParticipantProfile and update the internal Participant entity with correlation id, identifier, and VPAs
        var tmProfile = tenantManagerClient.deployParticipantProfile(tenant.getCorrelationId(), new ParticipantProfile(
                UUID.randomUUID().toString(), 0L, deployment.identifier(), tenant.getCorrelationId(), false, null, Map.of(), Map.of(), Collections.emptyList()
        ));
        participant.setCorrelationId(tmProfile.id());
        participant.setIdentifier(tmProfile.identifier());


        participant.getAgents().clear();
        participant.getAgents().addAll(tmProfile.vpas().stream().map(apiVpa -> new com.metaformsystems.redline.domain.entity.VirtualParticipantAgent(com.metaformsystems.redline.domain.entity.VirtualParticipantAgent.VpaType.fromCfmName(apiVpa.type()), DeploymentState.valueOf(apiVpa.state().toUpperCase()))).collect(Collectors.toSet()));

        // wait for participants to be ready
        var saved = participantRepository.save(participant);

// Eagerly load credentials from Vault so downstream calls (file upload, catalog, etc.)
// don't NPE when they try to read clientCredentials. This mirrors what getParticipant()
// lazily does on read, but does it at deploy time so the first call after deploy works.
        try {
            var pcId = extractParticipantContextId(tmProfile);
            if (pcId != null) {
                saved.setParticipantContextId(pcId);
                if (saved.getClientCredentials() == null) {
                    getClientCredentials(pcId);  // populates saved.clientCredentials as a side effect
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to eagerly load credentials for participant {}: {}", saved.getId(), ex.getMessage());
            // Non-fatal — getParticipant() will retry on next read.
        }

        return toParticipantResource(saved);
    }

    @Transactional
    public String getParticipantContextId(Long participantId) {
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var participantCorrelationId = participant.getCorrelationId();
        var cfmProfile = tenantManagerClient.getParticipantProfile(participant.getTenant().getCorrelationId(), participantCorrelationId);

        var pcId = extractParticipantContextId(cfmProfile);
        participant.setParticipantContextId(pcId);
        return pcId;
    }

    /**
     * Get the client credentials for a participant, which is necessary to access the participant's APIs in the
     * control plane and identity hub later
     *
     * @param participantContextId the Participant Context ID that was created by the tenant manager. Use {@link #getParticipantContextId(Long)} to retrieve it.
     */
    @Transactional
    public ClientCredentials getClientCredentials(String participantContextId) {
        var secret = vaultClient.readSecret("/v1/secret/data/%s".formatted(participantContextId));
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        //todo: store credentials somewhere safer!
        var participantProfile = participantRepository.findByParticipantContextId(participantContextId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with participantContextId id: " + participantContextId));

        var clientCredentials = new ClientCredentials(participantContextId, secret);
        participantProfile.setClientCredentials(clientCredentials);

        return clientCredentials;
    }

    @Transactional
    public Participant getParticipant(Long id) {

        var profile = participantRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + id));

        // fixme: figure out a better way to synchronize redline with CFM (periodically, NATS, etc.)
        // update VPA state
        var cfmProfile = tenantManagerClient.getParticipantProfile(profile.getTenant().getCorrelationId(), profile.getCorrelationId());
        profile.setParticipantContextId(extractParticipantContextId(cfmProfile));

        // update credentials
        ofNullable(profile.getClientCredentials()).orElseGet(() -> getClientCredentials(profile.getParticipantContextId()));

        // update VPA deployment state
        cfmProfile.vpas().forEach(cfmVpa -> {
            var type = com.metaformsystems.redline.domain.entity.VirtualParticipantAgent.VpaType.fromCfmName(cfmVpa.type());
            ofNullable(profile.getAgentForType(type)).ifPresentOrElse(agent -> agent.setState(DeploymentState.valueOf(cfmVpa.state().toUpperCase())),
                    () -> log.warn("VPA received {} from CFM, but not found in participant {}", cfmVpa.type(), profile.getIdentifier()));
        });

        // No need to save - changes will be automatically persisted at transaction end
        return toParticipantResource(profile);
    }

    @Transactional
    public PartnerReference createPartnerReference(Long providerId, Long tenantId, Long participantId, Long dataspaceId, PartnerReferenceRequest request) {
        // Find participant first
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));

        // Verify participant belongs to the specified tenant
        if (participant.getTenant() == null || !participant.getTenant().getId().equals(tenantId)) {
            throw new ObjectNotFoundException("Participant " + participantId + " does not belong to tenant " + tenantId);
        }

        // Verify tenant belongs to the specified service provider
        var tenant = participant.getTenant();
        if (tenant.getServiceProvider() == null || !tenant.getServiceProvider().getId().equals(providerId)) {
            throw new ObjectNotFoundException("Tenant " + tenantId + " does not belong to service provider " + providerId);
        }

        // Find dataspace info
        var dataspaceInfo = participant.getDataspaceInfos().stream()
                .filter(i -> i.getDataspaceId().equals(dataspaceId))
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException("Dataspace info not found for participant " + participantId + " and dataspace " + dataspaceId));

        // Create and add partner reference
        var partnerReference = new com.metaformsystems.redline.domain.entity.PartnerReference(
                request.identifier(),
                request.nickname(),
                request.properties() != null ? request.properties() : new java.util.HashMap<>()
        );

        dataspaceInfo.getPartners().add(partnerReference);
        participantRepository.save(participant);

        return new PartnerReference(partnerReference.identifier(), partnerReference.nickname(), partnerReference.properties());
    }

    @Transactional
    public List<PartnerReference> getPartnerReferences(Long participantId, Long dataspacesId) {
        return participantRepository.findById(participantId).stream()
                .flatMap(p -> p.getDataspaceInfos().stream())
                .filter(i -> i.getDataspaceId().equals(dataspacesId))
                .flatMap(i -> i.getPartners().stream())
                .map(r -> new PartnerReference(r.identifier(), r.nickname(), r.properties()))
                .toList();
    }

    @Transactional
    public List<DataspaceResponse> getParticipantDataspaces(Long participantId) {
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var dataspaceIds = participant.getDataspaceInfos().stream()
                .map(DataspaceInfo::getDataspaceId)
                .toList();
        if (dataspaceIds.isEmpty()) {
            return List.of();
        }
        var dataspaces = dataspaceRepository.findAllById(dataspaceIds);
        return dataspaces.stream()
                .map(ds -> new DataspaceResponse(ds.getId(), ds.getName(), ds.getProperties()))
                .toList();
    }

    public void registerDataPlane(Long participantId, DataPlaneRegistrationRequest request) {
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ObjectNotFoundException("Participant not found with id: " + participantId));
        var participantContextId = participant.getParticipantContextId();

        //todo: replace magic strings with parameters
        managementApiClient.prepareDataplane(participantContextId, DataplaneRegistration.Builder.aDataplaneRegistration()
                .id(request.id().formatted(participantContextId))
                .allowedTransferTypes(request.allowedTransferTypes())
                .labels(request.allowedTransferTypes())
                .url(request.url().formatted(participantContextId))
                .build());
    }

    private @Nullable String extractParticipantContextId(ParticipantProfile participant) {

        var props = participant.properties();
        if (props != null && props.containsKey(STATE_PROPERTY_KEY) && props.get(STATE_PROPERTY_KEY) instanceof Map stateMap) {
            var credentialRequestUrl = stateMap.get("credentialRequestUrl");
            var holderPid = stateMap.get("holderPid");
            var participantContextId = stateMap.get("participantContextId");

            return participantContextId.toString();
        }
        return null;
    }

    @NonNull
    private Participant toParticipantResource(com.metaformsystems.redline.domain.entity.Participant saved) {
        var vpas = saved.getAgents().stream().map(vpa -> new VirtualParticipantAgent(vpa.getId(),
                VirtualParticipantAgent.Type.valueOf(vpa.getType().name()),
                com.metaformsystems.redline.api.dto.response.DeploymentState.valueOf(vpa.getState().name()))).toList();
        var infos = saved.getDataspaceInfos().stream()
                .map(entity -> new com.metaformsystems.redline.api.dto.response.DataspaceInfo(
                        entity.getId(),
                        entity.getDataspaceId(),
                        entity.getAgreementTypes(),
                        entity.getRoles(),
                        entity.getProperties()))
                .toList();
        return new Participant(saved.getId(), saved.getIdentifier(), vpas, infos);
    }

    @NonNull
    private Tenant toTenantResource(com.metaformsystems.redline.domain.entity.Tenant t) {
        var participants = t.getParticipants().stream()
                .map(this::toParticipantResource).toList();
        return new Tenant(t.getId(), t.getServiceProvider().getId(), t.getName(), participants, t.getProperties());
    }


}
