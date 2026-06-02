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

package com.metaformsystems.redline.infrastructure.client.tenantmanager;

import com.metaformsystems.redline.application.service.TokenProvider;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.TenantManagerClientImpl;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.CellCreationRequest;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.ModelQuery;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.ParticipantProfile;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.TenantCreationRequest;
import com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto.TenantPropertiesDiff;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TenantManagerClientImpl Tests")
class TenantManagerClientImplTest {

    private static final String TEST_TOKEN = "test-token";
    private final TokenProvider tokenProvider = mock();
    private MockWebServer mockWebServer;
    private TenantManagerClientImpl tenantManagerClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(InetAddress.getByName("localhost"), TestSocketUtils.findAvailableTcpPort());

        var webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        when(tokenProvider.getToken("provisioner", "provisioner-secret", "identity-api:read"))
                .thenReturn(TEST_TOKEN);

        tenantManagerClient = new TenantManagerClientImpl(webClient, tokenProvider, "provisioner", "provisioner-secret");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    // Cell Tests
    @Test
    @DisplayName("should list all cells successfully")
    void listCells_success() {
        var responseBody = """
                [
                    {
                        "id": "cell-1",
                        "version": 1,
                        "state": "ACTIVE",
                        "stateTimestamp": "2026-01-22T10:00:00Z",
                        "externalId": "ext-cell-1",
                        "properties": {}
                    },
                    {
                        "id": "cell-2",
                        "version": 1,
                        "state": "ACTIVE",
                        "stateTimestamp": "2026-01-22T10:00:00Z",
                        "externalId": "ext-cell-2",
                        "properties": {}
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listCells();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("cell-1", result.get(0).id());
        assertEquals("cell-2", result.get(1).id());
    }

    @Test
    @DisplayName("should return empty list when no cells exist")
    void listCells_empty() {
        var responseBody = "[]";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listCells();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("should create a cell successfully")
    void createCell_success() throws InterruptedException {
        var responseBody = """
                {
                    "id": "cell-1",
                    "version": 1,
                    "state": "ACTIVE",
                    "stateTimestamp": "2026-01-22T10:00:00Z",
                    "externalId": "ext-cell-1",
                    "properties": {}
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var newCell = createNewCell();
        var result = tenantManagerClient.createCell(newCell);

        assertNotNull(result);
        assertEquals("cell-1", result.id());
        assertEquals("ACTIVE", result.state());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
    }

    // Dataspace Profile Tests
    @Test
    @DisplayName("should list all dataspace profiles successfully")
    void listDataspaceProfiles_success() {
        var responseBody = """
                [
                    {
                        "id": "profile-1",
                        "name": "Default Profile",
                        "version": 1
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listDataspaceProfiles();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("profile-1", result.getFirst().id());
    }

    @Test
    @DisplayName("should return empty list when no dataspace profiles exist")
    void listDataspaceProfiles_empty() {
        var responseBody = "[]";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listDataspaceProfiles();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("should get dataspace profile by id successfully")
    void getDataspaceProfile_success() throws InterruptedException {
        var profileId = "profile-1";
        var responseBody = """
                {
                    "id": "profile-1",
                    "name": "Default Profile",
                    "version": 1
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.getDataspaceProfile(profileId);

        assertNotNull(result);
        assertEquals("profile-1", result.id());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("GET", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should deploy dataspace profile successfully")
    void deployDataspaceProfile_success() throws InterruptedException {
        var profileId = "profile-1";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204)
                .addHeader("Content-Type", "application/json"));

        tenantManagerClient.deployDataspaceProfile(profileId);

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
    }

    // Participant Profile Tests
    @Test
    @DisplayName("should query participant profiles successfully")
    void queryParticipantProfiles_success() {
        var responseBody = """
                [
                    {
                        "id": "participant-1",
                        "tenantId": "tenant-1",
                        "participantId": "did:example:123"
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var query = createModelQuery();
        var result = tenantManagerClient.queryParticipantProfiles(query);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("participant-1", result.getFirst().id());
    }

    @Test
    @DisplayName("should return empty list when no participant profiles match query")
    void queryParticipantProfiles_empty() {
        var responseBody = "[]";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var query = createModelQuery();
        var result = tenantManagerClient.queryParticipantProfiles(query);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("should list participant profiles by tenant id successfully")
    void listParticipantProfiles_success() throws InterruptedException {
        var tenantId = "tenant-1";
        var responseBody = """
                [
                    {
                        "id": "participant-1",
                        "tenantId": "tenant-1",
                        "participantId": "did:example:123"
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listParticipantProfiles(tenantId);

        assertNotNull(result);
        assertEquals(1, result.size());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("GET", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should get participant profile by tenant id and participant id successfully")
    void getParticipantProfile_success() throws InterruptedException {
        var tenantId = "tenant-1";
        var participantId = "participant-1";
        var responseBody = """
                {
                    "id": "participant-1",
                    "tenantId": "tenant-1",
                    "participantId": "did:example:123"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.getParticipantProfile(tenantId, participantId);

        assertNotNull(result);
        assertEquals("participant-1", result.id());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("GET", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should deploy participant profile successfully")
    void deployParticipantProfile_success() throws InterruptedException {
        var tenantId = "tenant-1";
        var responseBody = """
                {
                    "id": "participant-1",
                    "tenantId": "tenant-1",
                    "participantId": "did:example:123"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var profile = createParticipantProfile();
        var result = tenantManagerClient.deployParticipantProfile(tenantId, profile);

        assertNotNull(result);
        assertEquals("participant-1", result.id());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should delete participant profile successfully")
    void deleteParticipantProfile_success() throws InterruptedException {
        var tenantId = "tenant-1";
        var participantId = "participant-1";
        var responseBody = """
                {
                    "id": "participant-1",
                    "tenantId": "tenant-1",
                    "participantId": "did:example:123"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.deleteParticipantProfile(tenantId, participantId);

        assertNotNull(result);
        assertEquals("participant-1", result.id());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("DELETE", recordedRequest.getMethod());
    }

    // Tenant Tests
    @Test
    @DisplayName("should list all tenants successfully")
    void listTenants_success() {
        var responseBody = """
                [
                    {
                        "id": "tenant-1",
                        "version": 1,
                        "properties": {}
                    },
                    {
                        "id": "tenant-2",
                        "version": 1,
                        "properties": {}
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listTenants();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("tenant-1", result.get(0).id());
        assertEquals("tenant-2", result.get(1).id());
    }

    @Test
    @DisplayName("should return empty list when no tenants exist")
    void listTenants_empty() {
        var responseBody = "[]";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.listTenants();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("should get tenant by id successfully")
    void getTenant_success() throws InterruptedException {
        var tenantId = "tenant-1";
        var responseBody = """
                {
                    "id": "tenant-1",
                    "version": 1,
                    "properties": {}
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var result = tenantManagerClient.getTenant(tenantId);

        assertNotNull(result);
        assertEquals("tenant-1", result.id());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("GET", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should create tenant successfully")
    void createTenant_success() throws InterruptedException {
        var responseBody = """
                {
                    "id": "tenant-1",
                    "version": 1,
                    "properties": {}
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var newTenant = createNewTenant();
        var result = tenantManagerClient.createTenant(newTenant);

        assertNotNull(result);
        assertEquals("tenant-1", result.id());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should update tenant successfully")
    void updateTenant_success() throws InterruptedException {
        var tenantId = "tenant-1";
        var responseBody = """
                {
                    "id": "tenant-1",
                    "version": 2,
                    "properties": {}
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var diff = createTenantPropertiesDiff();
        var result = tenantManagerClient.updateTenant(tenantId, diff);

        assertNotNull(result);
        assertEquals("tenant-1", result.id());
        assertEquals(2L, result.version());

        var recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("PATCH", recordedRequest.getMethod());
    }

    @Test
    @DisplayName("should query tenants successfully")
    void queryTenants_success() {
        var responseBody = """
                [
                    {
                        "id": "tenant-1",
                        "version": 1,
                        "properties": {}
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var query = createModelQuery();
        var result = tenantManagerClient.queryTenants(query);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("tenant-1", result.getFirst().id());
    }

    @Test
    @DisplayName("should return empty list when no tenants match query")
    void queryTenants_empty() {
        var responseBody = "[]";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        var query = createModelQuery();
        var result = tenantManagerClient.queryTenants(query);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // Helper methods to create DTO instances
    private CellCreationRequest createNewCell() {
        var properties = new HashMap<String, Object>();
        properties.put("name", "test-cell");
        return new CellCreationRequest(
                "ACTIVE",
                java.time.OffsetDateTime.now(),
                "ext-cell-1",
                properties
        );
    }

    private TenantCreationRequest createNewTenant() {
        var properties = new HashMap<String, Object>();
        properties.put("name", "test-tenant");
        return new TenantCreationRequest(properties);
    }

    private ModelQuery createModelQuery() {
        return new ModelQuery("test", 10L, 0L);
    }

    private ParticipantProfile createParticipantProfile() {
        return new ParticipantProfile(
                "participant-1",
                1L,
                "did:example:123",
                "tenant-1",
                false,
                null,
                new HashMap<>(),
                new HashMap<>(),
                List.of()
        );
    }

    private TenantPropertiesDiff createTenantPropertiesDiff() {
        var addedProperties = new HashMap<String, Object>();
        addedProperties.put("key", "value");
        return new TenantPropertiesDiff(addedProperties, List.of());
    }
}
