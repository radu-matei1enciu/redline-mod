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

package com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1;

import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.Cell;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.CellCreationRequest;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.DataspaceProfile;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.ModelQuery;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.ParticipantProfile;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.Tenant;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.TenantCreationRequest;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.TenantPropertiesDiff;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class TenantManagerClientImpl implements TenantManagerClient {

    private static final String API_BASE = "/api/v1alpha1";
    private final WebClient webClient;

    public TenantManagerClientImpl(WebClient tenantManagerWebClient) {
        this.webClient = tenantManagerWebClient;
    }

    @Override
    public List<Cell> listCells() {
        return webClient.get()
                .uri(API_BASE + "/cells")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Cell>>() {
                })
                .block();
    }

    @Override
    public Cell createCell(CellCreationRequest cellCreationRequest) {
        return webClient.post()
                .uri(API_BASE + "/cells")
                .bodyValue(cellCreationRequest)
                .retrieve()
                .bodyToMono(Cell.class)
                .block();
    }

    @Override
    public List<DataspaceProfile> listDataspaceProfiles() {
        return webClient.get()
                .uri(API_BASE + "/dataspace-profiles")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<DataspaceProfile>>() {
                })
                .block();
    }

    @Override
    public DataspaceProfile getDataspaceProfile(String id) {
        return webClient.get()
                .uri(API_BASE + "/dataspace-profiles/{id}", id)
                .retrieve()
                .bodyToMono(DataspaceProfile.class)
                .block();
    }

    @Override
    public void deployDataspaceProfile(String id) {
        webClient.post()
                .uri(API_BASE + "/dataspace-profiles/{id}/deployments", id)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public List<ParticipantProfile> queryParticipantProfiles(ModelQuery query) {
        return webClient.post()
                .uri(API_BASE + "/participant-profiles/query")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ParticipantProfile>>() {
                })
                .block();
    }

    @Override
    public List<ParticipantProfile> listParticipantProfiles(String tenantId) {
        return webClient.get()
                .uri(API_BASE + "/tenants/{id}/participant-profiles", tenantId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ParticipantProfile>>() {
                })
                .block();
    }

    @Override
    public ParticipantProfile getParticipantProfile(String tenantId, String participantId) {
        return webClient.get()
                .uri(API_BASE + "/tenants/{id}/participant-profiles/{participantID}", tenantId, participantId)
                .retrieve()
                .bodyToMono(ParticipantProfile.class)
                .block();
    }

    @Override
    public ParticipantProfile deployParticipantProfile(String tenantId, ParticipantProfile profile) {
        return webClient.post()
                .uri(API_BASE + "/tenants/{id}/participant-profiles", tenantId)
                .bodyValue(profile)
                .retrieve()
                .bodyToMono(ParticipantProfile.class)
                .block();
    }

    @Override
    public ParticipantProfile deleteParticipantProfile(String tenantId, String participantId) {
        return webClient.delete()
                .uri(API_BASE + "/tenants/{id}/participant-profiles/{participantID}", tenantId, participantId)
                .retrieve()
                .bodyToMono(ParticipantProfile.class)
                .block();
    }

    @Override
    public List<Tenant> listTenants() {
        return webClient.get()
                .uri(API_BASE + "/tenants")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Tenant>>() {
                })
                .block();
    }

    @Override
    public Tenant getTenant(String id) {
        return webClient.get()
                .uri(API_BASE + "/tenants/{id}", id)
                .retrieve()
                .bodyToMono(Tenant.class)
                .block();
    }

    @Override
    public Tenant createTenant(TenantCreationRequest newTenant) {
        return webClient.post()
                .uri(API_BASE + "/tenants")
                .bodyValue(newTenant)
                .retrieve()
                .bodyToMono(Tenant.class)
                .block();
    }

    @Override
    public Tenant updateTenant(String id, TenantPropertiesDiff diff) {
        return webClient.patch()
                .uri(API_BASE + "/tenants/{id}", id)
                .bodyValue(diff)
                .retrieve()
                .bodyToMono(Tenant.class)
                .block();
    }

    @Override
    public List<Tenant> queryTenants(ModelQuery query) {
        return webClient.post()
                .uri(API_BASE + "/tenants/query")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Tenant>>() {
                })
                .block();
    }

    @Override
    public void joinDataspace(String tenantId, String participantId, String dataspaceProfileId) {
        webClient.post()
                .uri(API_BASE + "/tenants/{tenantId}/participant-profiles/{participantId}/dataspaces/{dataspaceProfileId}/join",
                        tenantId, participantId, dataspaceProfileId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}

