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

import java.util.List;

/**
 * HTTP client for the Tenant Manager API
 */
public interface TenantManagerClient {

    // Cell operations
    List<Cell> listCells();

    Cell createCell(CellCreationRequest cellCreationRequest);

    // Dataspace Profile operations
    List<DataspaceProfile> listDataspaceProfiles();

    DataspaceProfile getDataspaceProfile(String id);

    void deployDataspaceProfile(String id);

    // Participant Profile operations
    List<ParticipantProfile> queryParticipantProfiles(ModelQuery query);

    List<ParticipantProfile> listParticipantProfiles(String tenantId);

    ParticipantProfile getParticipantProfile(String tenantId, String participantId);

    ParticipantProfile deployParticipantProfile(String tenantId, ParticipantProfile profile);

    ParticipantProfile deleteParticipantProfile(String tenantId, String participantId);

    // Tenant operations
    List<Tenant> listTenants();

    Tenant getTenant(String id);

    Tenant createTenant(TenantCreationRequest newTenant);

    Tenant updateTenant(String id, TenantPropertiesDiff diff);

    List<Tenant> queryTenants(ModelQuery query);

    //void joinDataspace(String tenantId, String participantId, String dataspaceProfileId);
}
