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

package com.metaformsystems.redline.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an identity participating in one or more dataspaces.
 */
@Entity
@Table(name = "participants")
public class Participant extends VersionedEntity {

    private String identifier;
    private String correlationId;

    @ManyToOne
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "participant_id")
    private Set<DataspaceInfo> dataspaceInfos = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "participant_id")
    private Set<VirtualParticipantAgent> agents = new HashSet<>();

    private String participantContextId;
    @Embedded
    private ClientCredentials clientCredentials;


    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "uploaded_files")
    private List<UploadedFile> uploadedFiles = new ArrayList<>();

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Set<DataspaceInfo> getDataspaceInfos() {
        return dataspaceInfos;
    }

    public void setDataspaceInfos(Set<DataspaceInfo> dataspaces) {
        this.dataspaceInfos = dataspaces;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Tenant getTenant() {
        return tenant;
    }

    /**
     * careful when using this: we typically have to also call to maintain synchronicity {@link Tenant#addParticipant(Participant)}
     */
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Set<VirtualParticipantAgent> getAgents() {
        return agents;
    }

    public void setAgents(Set<VirtualParticipantAgent> agents) {
        this.agents = agents;
    }

    public String getParticipantContextId() {
        return participantContextId;
    }

    public void setParticipantContextId(String participantContextId) {
        this.participantContextId = participantContextId;
    }

    public ClientCredentials getClientCredentials() {
        return clientCredentials;
    }

    public void setClientCredentials(ClientCredentials clientCredentials) {
        this.clientCredentials = clientCredentials;
    }

    public VirtualParticipantAgent getAgentForType(VirtualParticipantAgent.VpaType type) {
        return agents.stream().filter(agent -> agent.getType() == type).findFirst().orElse(null);
    }

    public List<UploadedFile> getUploadedFiles() {
        return uploadedFiles;
    }

    public void setUploadedFiles(List<UploadedFile> uploadedFiles) {
        this.uploadedFiles = uploadedFiles;
    }

    public void addDataspaceInfo(DataspaceInfo info) {
        this.dataspaceInfos.add(info);
    }
}
