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
 *       Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V. - OpenAPI and file upload
 *
 */

package com.metaformsystems.redline.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaformsystems.redline.api.dto.request.ContractRequest;
import com.metaformsystems.redline.api.dto.request.CounterPartyIdWrapper;
import com.metaformsystems.redline.api.dto.request.TransferProcessRequest;
import com.metaformsystems.redline.api.dto.response.Contract;
import com.metaformsystems.redline.api.dto.response.ContractNegotiation;
import com.metaformsystems.redline.api.dto.response.FileResource;
import com.metaformsystems.redline.domain.service.DataAccessService;
import com.metaformsystems.redline.infrastructure.client.management.dto.Catalog;
import com.metaformsystems.redline.infrastructure.client.management.dto.CelExpression;
import com.metaformsystems.redline.infrastructure.client.management.dto.Constraint;
import com.metaformsystems.redline.infrastructure.client.management.dto.Obligation;
import com.metaformsystems.redline.infrastructure.client.management.dto.Offer;
import com.metaformsystems.redline.infrastructure.client.management.dto.Permission;
import com.metaformsystems.redline.infrastructure.client.management.dto.PolicySet;
import com.metaformsystems.redline.infrastructure.client.management.dto.Prohibition;
import com.metaformsystems.redline.infrastructure.client.management.dto.TransferProcess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "EDC data operations", description = "UI API for uploading and downloading data, managing EDC data transfers, and related operations")
@RequestMapping(value = "/api/ui", produces = MediaType.APPLICATION_JSON_VALUE)
public class EdcDataController {

    private final DataAccessService dataAccessService;
    private final ObjectMapper objectMapper;

    public EdcDataController(DataAccessService dataAccessService, ObjectMapper objectMapper) {
        this.dataAccessService = dataAccessService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "service-providers/{providerId}/dataspaces/{dataspaceId}/tenants/{tenantId}/participants/{participantId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Upload a file", description = "Uploads a file for a specific participant with associated metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File successfully uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid file or metadata"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error during file upload")
    })
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    @Parameter(name = "dataspaceId", description = "Database ID of the data space", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    public ResponseEntity<Void> uploadFile(@PathVariable Long participantId,
                                           @PathVariable Long tenantId,
                                           @PathVariable Long providerId,
                                           @PathVariable Long dataspaceId,
                                           @RequestPart("publicMetadata") Map<String, Object> publicMetadata,
                                           @RequestPart("privateMetadata") Map<String, Object> privateMetadata,
                                           @RequestPart(value = "celExpressions", required = false) List<CelExpression> celExpressions,
                                           @RequestPart(value = "policySet", required = false) PolicySet policySet,
                                           @RequestPart("file") MultipartFile file) {
        try {
            dataAccessService.uploadFileForParticipant(
                    participantId,
                    publicMetadata,
                    privateMetadata,
                    file.getInputStream(),
                    file.getContentType(),
                    file.getOriginalFilename(),
                    celExpressions != null ? celExpressions : List.of(),
                    policySet
            );
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(null);
    }

    @GetMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/files")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "List files", description = "Retrieves a list of all files associated with a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved file list"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found")
    })
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    public ResponseEntity<List<FileResource>> listFiles(@PathVariable Long participantId,
                                                        @PathVariable Long tenantId,
                                                        @PathVariable Long providerId) {
        var files = dataAccessService.listFilesForParticipant(participantId);
        return ResponseEntity.ok(files);
    }

    @PostMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/catalog")
    @Operation(summary = "Request catalog", description = "Requests a catalog from a counter-party participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved catalog",
                    content = @Content(schema = @Schema(implementation = Catalog.class))),
            @ApiResponse(responseCode = "400", description = "Invalid counter-party identifier"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    public ResponseEntity<Catalog> requestCatalog(@RequestHeader(name = "Cache-Control", required = false, defaultValue = "no-cache") String cacheControl,
                                                  @PathVariable Long providerId,
                                                  @PathVariable Long tenantId,
                                                  @PathVariable Long participantId,
                                                  @RequestBody CounterPartyIdWrapper counterPartyIdentifierWrapper) {

        var catalog = dataAccessService.requestCatalog(participantId, counterPartyIdentifierWrapper.counterPartyIdentifier(), cacheControl);
        return ResponseEntity.ok(catalog);
    }

    @GetMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/transfers")
    @Operation(summary = "List transfer processes", description = "Retrieves a list of all transfer processes associated with a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transfer process list. May be empty."),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred while processing the request")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    //    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransferProcess>> listTransferProcesses(@PathVariable Long providerId,
                                                                       @PathVariable Long tenantId,
                                                                       @PathVariable Long participantId) {
        return ResponseEntity.ok(dataAccessService.listTransferProcesses(participantId));
    }

    @GetMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/contracts")
    @Operation(summary = "List transfer processes", description = "Retrieves a list of all contracts (pending and agreed-on) associated with a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts list. May be empty."),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred while processing the request")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    //    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Contract>> listContracts(@PathVariable Long providerId,
                                                        @PathVariable Long tenantId,
                                                        @PathVariable Long participantId) {
        var contractNegotiations = dataAccessService.listContracts(participantId);
        var contracts = contractNegotiations.stream().map(cn -> {
            var builder = Contract.Builder.aContract()
                    .counterParty(cn.getCounterPartyId())
                    .type(cn.getType());

            if (cn.getContractAgreement() != null) {
                builder.id(cn.getContractAgreement().getId());
                builder.agreementId(cn.getContractAgreement().getAgreementId());
                builder.assetId(cn.getContractAgreement().getAssetId());
                builder.signingDate(Instant.ofEpochSecond(cn.getContractAgreement().getContractSigningDate()));
                builder.provider(cn.getContractAgreement().getProviderId());
                builder.consumer(cn.getContractAgreement().getConsumerId());
                builder.policy(cn.getContractAgreement().getPolicy());
                builder.pending(false);
            }

            return builder.build();
        }).toList();
        return ResponseEntity.ok(contracts);
    }

    @Operation(summary = "Initiate a contract negotiation", description = "Triggers a contract negotiation with a counter-party based on the provided contract request details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract Negotiation started successfully."),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred while processing the request")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    @PostMapping(value = "service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/contracts", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> requestContract(@PathVariable Long providerId,
                                                  @PathVariable Long tenantId,
                                                  @PathVariable Long participantId,
                                                  @RequestBody ContractRequest contractRequest) {

        var offer = Offer.Builder.anOffer()
                .target(contractRequest.getAssetId())
                .id(contractRequest.getOfferId())
                .assigner(contractRequest.getProviderId());

        if (contractRequest.getProhibitions() != null) {
            var prohibition = new Prohibition();
            prohibition.setConstraint(contractRequest.getProhibitions().stream().map(dto -> new Constraint(dto.leftOperand(), dto.operator(), dto.rightOperand())).toList());
            offer.prohibition(List.of(prohibition));
        }

        if (contractRequest.getPermissions() != null) {
            var permission = new Permission();
            permission.setConstraint(contractRequest.getPermissions().stream().map(dto -> new Constraint(dto.leftOperand(), dto.operator(), dto.rightOperand())).toList());
            offer.permission(List.of(permission));
        }

        if (contractRequest.getObligations() != null) {
            var obligation = new Obligation();
            obligation.setConstraint(contractRequest.getObligations().stream().map(dto -> new Constraint(dto.leftOperand(), dto.operator(), dto.rightOperand())).toList());
            offer.obligation(List.of(obligation));
        }

        var request = com.metaformsystems.redline.infrastructure.client.management.dto.ContractRequest.Builder.aContractRequest()
                .providerId(contractRequest.getProviderId())
                .policy(offer.build())
                //counterparty address is left empty - the tenant service must resolve this from the DID
                .build();

        return ResponseEntity.ok(dataAccessService.initiateContractNegotiation(participantId, request));
    }

    @GetMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/contracts/{contractNegotiationId}")
    @Operation(summary = "Get a contract negotiation", description = "Gets details about a specific contract negotiation")

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract Negotiation obtained successfully."),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred while processing the request")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    @Parameter(name = "contractNegotiationId", description = "EDC-ID of the contract negotiation", required = true)
    public ResponseEntity<ContractNegotiation> getContractNegotiation(@PathVariable Long providerId,
                                                                      @PathVariable Long tenantId,
                                                                      @PathVariable Long participantId,
                                                                      @PathVariable String contractNegotiationId) {
        var contractNegotiation = dataAccessService.getContractNegotiation(participantId, contractNegotiationId);

        var dto = ContractNegotiation.Builder.aContractNegotiationDto()
                .id(contractNegotiation.getId())
                .state(contractNegotiation.getState())
                .correlationId(contractNegotiation.getCorrelationId())
                .counterPartyId(contractNegotiation.getCounterPartyId())
                .counterPartyAddress(contractNegotiation.getCounterPartyAddress())
                .protocol(contractNegotiation.getProtocol())
                .participantContextId(contractNegotiation.getParticipantContextId())
                .type(contractNegotiation.getType())
                .contractAgreementId(contractNegotiation.getContractAgreementId())
                .contractOffers(contractNegotiation.getContractOffers())
                .build();

        return ResponseEntity.ok(dto);
    }


    @PostMapping(value = "service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/transfers", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Initiate a transfer process", description = "Triggers a transfer process with a counter-party based on the provided contract agreement details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer process started successfully."),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred while processing the request")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    public ResponseEntity<String> requestTransfer(@PathVariable Long providerId,
                                                  @PathVariable Long tenantId,
                                                  @PathVariable Long participantId,
                                                  @RequestBody TransferProcessRequest transferRequest) {

        return ResponseEntity.ok(dataAccessService.initiateTransferProcess(participantId, transferRequest));
    }

    @GetMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/transfers/{transferProcessId}")
    public ResponseEntity<TransferProcess> getTransferProcess(@PathVariable Long providerId,
                                                              @PathVariable Long tenantId,
                                                              @PathVariable Long participantId,
                                                              @PathVariable String transferProcessId) {
        var transferProcess = dataAccessService.getTransferProcess(participantId, transferProcessId);
        return ResponseEntity.ok(transferProcess);
    }

    @Operation(summary = "Download file")
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    @GetMapping(value = "service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/files/{fileId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadData(@PathVariable Long providerId,
                                               @PathVariable Long tenantId,
                                               @PathVariable Long participantId,
                                               @PathVariable String fileId,
                                               @RequestHeader(name = "Authorization") String authorizationHeader) {
        var data = dataAccessService.downloadData(participantId, fileId, authorizationHeader);
        return ResponseEntity.ok(data);
    }

}
