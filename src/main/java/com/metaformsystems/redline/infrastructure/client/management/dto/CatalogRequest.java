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

package com.metaformsystems.redline.infrastructure.client.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class CatalogRequest {
    @JsonProperty("@context")
    private final String[] context = new String[]{
            "https://w3id.org/edc/connector/management/v2",
    };
    @JsonProperty("@type")
    private final String type = "CatalogRequest";
    private String protocol = "dataspace-protocol-http:2025-1";
    private String counterPartyAddress;
    private String counterPartyId;
    private List<String> additionalScopes = new ArrayList<>();

    public String[] getContext() {
        return context;
    }

    public String getType() {
        return type;
    }

    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getCounterPartyId() {
        return counterPartyId;
    }

    public List<String> getAdditionalScopes() {
        return additionalScopes;
    }

    public static final class Builder {
        private final CatalogRequest transferRequest;

        private Builder() {
            transferRequest = new CatalogRequest();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder counterPartyAddress(String counterPartyAddress) {
            transferRequest.counterPartyAddress = counterPartyAddress;
            return this;
        }

        public Builder protocol(String protocol) {
            transferRequest.protocol = protocol;
            return this;
        }

        public Builder counterPartyId(String counterPartyId) {
            transferRequest.counterPartyId = counterPartyId;
            return this;
        }

        public Builder additionalScopes(List<String> additionalScopes) {
            transferRequest.additionalScopes = new ArrayList<>(additionalScopes);
            return this;
        }

        public Builder addScope(String scope) {
            transferRequest.additionalScopes.add(scope);
            return this;
        }

        public CatalogRequest build() {
            return transferRequest;
        }
    }

    @Override
    public String toString() {
        return "CatalogRequest{" +
                "protocol='" + protocol + '\'' +
                ", counterPartyAddress='" + counterPartyAddress + '\'' +
                ", counterPartyId='" + counterPartyId + '\'' +
                ", additionalScopes=" + additionalScopes +
                '}';
    }
}