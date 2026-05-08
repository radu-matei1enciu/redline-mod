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
 *       Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V. - CEL and Constraint
 *
 */

package com.metaformsystems.redline.domain.service;

import com.metaformsystems.redline.application.service.TokenProvider;
import com.metaformsystems.redline.domain.entity.ClientCredentials;
import com.metaformsystems.redline.domain.entity.Dataspace;
import com.metaformsystems.redline.domain.entity.Participant;
import com.metaformsystems.redline.domain.entity.ServiceProvider;
import com.metaformsystems.redline.domain.entity.Tenant;
import com.metaformsystems.redline.domain.entity.UploadedFile;
import com.metaformsystems.redline.domain.repository.DataspaceRepository;
import com.metaformsystems.redline.domain.repository.ParticipantRepository;
import com.metaformsystems.redline.domain.repository.ServiceProviderRepository;
import com.metaformsystems.redline.domain.repository.TenantRepository;
import com.metaformsystems.redline.infrastructure.client.management.dto.CelExpression;
import com.metaformsystems.redline.infrastructure.client.management.dto.Constraint;
import com.metaformsystems.redline.infrastructure.client.management.dto.ContractRequest;
import com.metaformsystems.redline.infrastructure.client.management.dto.Obligation;
import com.metaformsystems.redline.infrastructure.client.management.dto.Offer;
import com.metaformsystems.redline.infrastructure.client.management.dto.PolicySet;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DataAccessServiceIntegrationTest {

    static final String mockBackEndHost = "localhost";
    static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();
    private static final String CATALOG_RESPONSE = """
            {
                "@type": "dcat:Catalog",
                "dcat:dataset": [],
                "dcat:service": []
            }
            """;
    private MockWebServer mockWebServer;
    @Autowired
    private DataAccessService dataAccessService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private DataspaceRepository dataspaceRepository;
    @Autowired
    private ServiceProviderRepository serviceProviderRepository;
    private ServiceProvider serviceProvider;
    @MockitoBean
    private TokenProvider tokenProvider;

    @MockitoBean
    private WebDidResolver webDidResolver;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tenant-manager.url", () -> "http://%s:%s/tm".formatted(mockBackEndHost, mockBackEndPort));
        registry.add("vault.url", () -> "http://%s:%s/vault".formatted(mockBackEndHost, mockBackEndPort));
        registry.add("controlplane.url", () -> "http://%s:%s/cp".formatted(mockBackEndHost, mockBackEndPort));
        registry.add("dataplane.url", () -> "http://%s:%s/dataplane".formatted(mockBackEndHost, mockBackEndPort));
        registry.add("dataplane.internal.url", () -> "http://%s:%s/dataplane".formatted(mockBackEndHost, mockBackEndPort));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // Create test data
        serviceProvider = new ServiceProvider();
        serviceProvider.setName("Test Provider");
        serviceProvider = serviceProviderRepository.save(serviceProvider);

        var dataspace = new Dataspace();
        dataspace.setName("Test Dataspace");
        dataspaceRepository.save(dataspace);

        mockWebServer = new MockWebServer();
        mockWebServer.start(InetAddress.getByName(mockBackEndHost), mockBackEndPort);
        when(tokenProvider.getToken(anyString(), anyString(), anyString())).thenReturn("mock-token");
    }

    @Test
    void shouldRequestCatalog_andCacheIt() {

        var participant = createAndSaveParticipant("ctx-1", "did:web:me");
        var counterParty = "did:web:them";

        // First call: Expect fetch from remote
        mockWebServer.enqueue(new MockResponse()
                .setBody(CATALOG_RESPONSE)
                .addHeader("Content-Type", "application/json"));


        var catalog1 = dataAccessService.requestCatalog(participant.getId(), counterParty, "max-age=3600");
        var catalog2 = dataAccessService.requestCatalog(participant.getId(), counterParty, "max-age=3600");


        assertThat(catalog1).isNotNull();
        assertThat(catalog2).isNotNull();
        // only 1 catalog request, second one is cached
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void shouldRequestCatalog_andBypassCacheWithNoCache() {
        var participant = createAndSaveParticipant("ctx-2", "did:web:me");
        var counterParty = "did:web:them";

        mockWebServer.enqueue(new MockResponse().setBody(CATALOG_RESPONSE).addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody(CATALOG_RESPONSE).addHeader("Content-Type", "application/json"));

        dataAccessService.requestCatalog(participant.getId(), counterParty, "max-age=3600");
        dataAccessService.requestCatalog(participant.getId(), counterParty, "no-cache");

        // both requests hit the remote catalog, no cache
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
//TODO Fix this test!

//    @Test
//    void shouldUploadFileWithCelExpressionsAndConstraints() {
//        var participant = createAndSaveParticipant("ctx-upload-1", "did:web:me");
//
//        // dataplane upload response
//        mockWebServer.enqueue(new MockResponse()
//                .setBody("{\"id\": \"generated-file-id-123\"}")
//                .addHeader("Content-Type", "application/json"));
//
//        // custom CEL expression
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
//
//        // asset creation
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
//
//        // membership CEL expression
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
//
//        // policy creation
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
//
//        // contract definition
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
//
//        var celExpressions = List.of(CelExpression.Builder.aNewCelExpression()
//                .id("custom-expression")
//                .leftOperand("CustomCredential")
//                .description("Custom expression")
//                .expression("true")
//                .scopes(Set.of("catalog"))
//                .build());
//
//        var policySet = new PolicySet(List.of(new PolicySet.Permission("use",
//                List.of(new PolicySet.Constraint("purpose", "eq", "test")))));
//
//        dataAccessService.uploadFileForParticipant(
//                participant.getId(),
//                new java.util.HashMap<>(Map.of("foo", "bar")),
//                new java.util.HashMap<>(Map.of("private", "value")),
//                new java.io.ByteArrayInputStream("file-data".getBytes()),
//                "text/plain",
//                "file.txt",
//                celExpressions,
//                policySet
//        );
//
//        assertThat(participantRepository.findById(participant.getId()))
//                .isPresent()
//                .hasValueSatisfying(p -> assertThat(p.getUploadedFiles()).hasSize(1));
//    }

    @Test
    void shouldRequestCatalog_andRefreshWhenMaxAgeIsZero() {
        var participant = createAndSaveParticipant("ctx-3", "did:web:me");
        var counterParty = "did:web:them";

        mockWebServer.enqueue(new MockResponse().setBody(CATALOG_RESPONSE).addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody(CATALOG_RESPONSE).addHeader("Content-Type", "application/json"));

        dataAccessService.requestCatalog(participant.getId(), counterParty, "max-age=3600");
        // max-age=0 should trigger expiration check effectively immediately or force refresh logic
        dataAccessService.requestCatalog(participant.getId(), counterParty, "max-age=0");

        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void shouldListContracts() throws InterruptedException {
        var participant = createAndSaveParticipant("ctx-4", "did:web:me");

        var contractsResponse = """
                [
                    {
                        "@id": "negotiation-1",
                        "@type": "ContractNegotiation",
                        "type": "CONSUMER",
                        "state": "FINALIZED",
                        "counterPartyId": "did:web:provider",
                        "counterPartyAddress": "http://provider.example.com/api/dsp",
                        "contractAgreementId": "agreement-1"
                    },
                    {
                        "@id": "negotiation-2",
                        "@type": "ContractNegotiation",
                        "type": "PROVIDER",
                        "state": "REQUESTED",
                        "counterPartyId": "did:web:consumer",
                        "counterPartyAddress": "http://consumer.example.com/api/dsp"
                    }
                ]
                """;

        var agreementResponse = """
                {
                    "@id": "agreement-1",
                    "@type": "ContractAgreement",
                    "providerId": "did:web:provider",
                    "consumerId": "did:web:consumer",
                    "assetId": "asset-1",
                    "policy": {
                        "@type": "Policy"
                    }
                }
                """;

        mockWebServer.enqueue(new MockResponse().setBody(contractsResponse).addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody(agreementResponse).addHeader("Content-Type", "application/json"));

        var result = dataAccessService.listContracts(participant.getId());

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(cn -> cn.getId().equals("negotiation-1") && cn.getState().equals("FINALIZED"));
        assertThat(result).anyMatch(cn -> cn.getId().equals("negotiation-2") && cn.getState().equals("REQUESTED"));
        assertThat(result.stream().filter(cn -> cn.getId().equals("negotiation-1")).findFirst().orElseThrow().getContractAgreement()).isNotNull();
        assertThat(result.stream().filter(cn -> cn.getId().equals("negotiation-2")).findFirst().orElseThrow().getContractAgreement()).isNull();

        var contractsRequest = mockWebServer.takeRequest();
        assertThat(contractsRequest.getPath()).isEqualTo("/cp/v5beta/participants/ctx-4/contractnegotiations/request");
        assertThat(contractsRequest.getMethod()).isEqualTo("POST");

        var agreementRequest = mockWebServer.takeRequest();
        assertThat(agreementRequest.getPath()).isEqualTo("/cp/v5beta/participants/ctx-4/contractnegotiations/negotiation-1/agreement");
        assertThat(agreementRequest.getMethod()).isEqualTo("GET");

    }

    @Test
    void shouldListFiles() {
        var participant = createAndSaveParticipant("ctx-5", "did:web:me");

        participant.setUploadedFiles(new ArrayList<>(List.of(
                new UploadedFile("file-id-1", "foobar.jpg", "image/jpeg", Map.of("bar", "baz")),
                new UploadedFile("file-id-2", "barbaz.pdf", "application/pdf", Map.of("quizz", "qazz"))
        )));

        participant = participantRepository.save(participant);

        var result = dataAccessService.listFilesForParticipant(participant.getId());

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(f -> f.fileId().equals("file-id-1") &&
                f.fileName().equals("foobar.jpg") &&
                f.contentType().equals("image/jpeg") &&
                f.metadata().get("bar").equals("baz"));
        assertThat(result).anyMatch(f -> f.fileId().equals("file-id-2") &&
                f.fileName().equals("barbaz.pdf") &&
                f.contentType().equals("application/pdf") &&
                f.metadata().get("quizz").equals("qazz"));
    }

    @Test
    void shouldInitiateContractNegotiation() throws InterruptedException {
        var participant = createAndSaveParticipant("ctx-6", "did:web:me");
        var providerId = "did:web:provider";
        var assetId = "asset-123";
        var offerId = "offer-456";

        when(webDidResolver.resolveProtocolEndpoints(eq(providerId)))
                .thenReturn("http://provider.example.com/api/dsp");

        var contractRequest = ContractRequest.Builder.aContractRequest()
                .providerId(providerId)
                .policy(Offer.Builder.anOffer()
                        .id(offerId)
                        .target(assetId)
                        .assigner(providerId)
                        .obligation(List.of(Obligation.Builder.anObligation()
                                .action("use")
                                .constraint(List.of(new Constraint("foo", "=", "bar")))
                                .build()))
                        .build())
                .build();

        mockWebServer.enqueue(new MockResponse()
                .setBody("{ \"@id\": \"negotiation-123\"}")
                .addHeader("Content-Type", "application/json"));

        var result = dataAccessService.initiateContractNegotiation(participant.getId(), contractRequest);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("negotiation-123");

        var request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/cp/v5beta/participants/ctx-6/contractnegotiations");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    private Participant createAndSaveParticipant(String contextId, String identifier) {
        var p = new Participant();
        p.setParticipantContextId(contextId);
        p.setIdentifier(identifier);
        p.setClientCredentials(new ClientCredentials("client-id", "client-secret"));
        p.setTenant(serviceProvider.getTenants().stream().findFirst().orElseGet(() -> {
            var t = new Tenant();
            t.setName("Test");
            t.setServiceProvider(serviceProvider);
            return tenantRepository.save(t);
        }));
        return participantRepository.save(p);
    }
}
