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

package com.metaformsystems.redline.api.controller;
import com.metaformsystems.redline.api.dto.request.JoinDataspaceRequest;
import com.metaformsystems.redline.api.dto.request.DataPlaneRegistrationRequest;
import com.metaformsystems.redline.api.dto.request.DataspaceRequest;
import com.metaformsystems.redline.api.dto.request.ParticipantDeployment;
import com.metaformsystems.redline.api.dto.request.PartnerReferenceRequest;
import com.metaformsystems.redline.api.dto.request.ServiceProvider;
import com.metaformsystems.redline.api.dto.request.TenantRegistration;
import com.metaformsystems.redline.api.dto.response.DataspaceResponse;
import com.metaformsystems.redline.api.dto.response.Participant;
import com.metaformsystems.redline.api.dto.response.PartnerReference;
import com.metaformsystems.redline.api.dto.response.ServiceProviderResponse;
import com.metaformsystems.redline.api.dto.response.Tenant;
import com.metaformsystems.redline.domain.service.ServiceProviderService;
import com.metaformsystems.redline.domain.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Main API controller for the Redline UI
 */
@RestController
@RequestMapping(value = "/api/ui", produces = "application/json")
@Tag(name = "Tenant operations", description = "UI API for managing dataspaces, service providers, tenants, and participants")
public class TenantController {
    private final ServiceProviderService serviceProviderService;
    private final TenantService tenantService;

    public TenantController(ServiceProviderService serviceProviderService, TenantService tenantService) {
        this.tenantService = tenantService;
        this.serviceProviderService = serviceProviderService;
    }

    @PostMapping("dataspaces")
    public ResponseEntity<DataspaceResponse> createDataspace(@RequestBody DataspaceRequest dataspace) {
        var saved = serviceProviderService.createDataspace(dataspace);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("dataspaces")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get all dataspaces", description = "Retrieves a list of all available dataspaces")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dataspaces")
    })
    public ResponseEntity<List<DataspaceResponse>> getDataspaces() {
        return ResponseEntity.ok(serviceProviderService.getDataspaces());
    }

    @PostMapping("service-providers/{serviceProviderId}/tenants/{tenantId}/participants/{participantId}/dataspaces/{dataspaceId}/join")
    @Operation(summary = "Join an additional dataspace with the current participant",
            description = "Adds a new Participant (with its own DID) to an existing participant for the specified dataspace.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully joined dataspace",
                    content = @Content(schema = @Schema(implementation = Participant.class))),
            @ApiResponse(responseCode = "404", description = "Tenant, participant or dataspace not found"),
            @ApiResponse(responseCode = "409", description = "Participant already in this dataspace")
    })
    public ResponseEntity<Participant> joinDataspace(@PathVariable Long serviceProviderId,
                                                     @PathVariable Long tenantId,
                                                     @PathVariable Long participantId,
                                                     @PathVariable Long dataspaceId,
                                                     @RequestBody(required = false) JoinDataspaceRequest request) {
        var roles = (request != null && request.roles() != null) ? request.roles() : java.util.List.<String>of();
        return ResponseEntity.ok(tenantService.joinAdditionalDataspace(tenantId, participantId, dataspaceId, roles));
    }

    @GetMapping("service-providers")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get all service providers", description = "Retrieves a list of all registered service providers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved service providers")
    })
    public ResponseEntity<List<ServiceProviderResponse>> getServiceProviders() {
        return ResponseEntity.ok(serviceProviderService.getServiceProviders());
    }

    @PostMapping("service-providers")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new service provider", description = "Registers a new service provider in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service provider successfully created",
                    content = @Content(schema = @Schema(implementation = ServiceProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid service provider data")
    })
    public ResponseEntity<ServiceProviderResponse> createServiceProvider(@RequestBody ServiceProvider serviceProvider) {
        var saved = serviceProviderService.createServiceProvider(serviceProvider);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("service-providers/{serviceProviderId}/tenants")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "List tenants", description = "List all tenants under a specific service provider.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenants Successfully retrieved",
                    content = @Content(schema = @Schema(implementation = Tenant.class))),
    })
    @Parameter(name = "serviceProviderId", description = "Database ID of the service provider", required = true)
    public ResponseEntity<List<Tenant>> listTenants(@PathVariable Long serviceProviderId) {
        var tenants = tenantService.getTenants(serviceProviderId);
        return ResponseEntity.ok(tenants);
    }

    @PostMapping("service-providers/{serviceProviderId}/tenants")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Register a new tenant", description = "Registers a new tenant under a specific service provider. A participant profile is also created.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant successfully registered",
                    content = @Content(schema = @Schema(implementation = Tenant.class))),
            @ApiResponse(responseCode = "400", description = "Invalid tenant registration data"),
            @ApiResponse(responseCode = "404", description = "Service provider not found")
    })
    @Parameter(name = "serviceProviderId", description = "Database ID of the service provider", required = true)
    public ResponseEntity<Tenant> registerTenant(@PathVariable Long serviceProviderId,
                                                 @RequestBody TenantRegistration registration) {
        var tenant = tenantService.registerTenant(serviceProviderId, registration);
        return ResponseEntity.ok(tenant);
    }

    @PostMapping("service-providers/{serviceProviderId}/tenants/{tenantId}/participants/{participantId}/deployments")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Deploy a participant", description = "Deploys a participant for a tenant. This will trigger the creation of resources in the dataspace.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant successfully deployed",
                    content = @Content(schema = @Schema(implementation = Participant.class))),
            @ApiResponse(responseCode = "400", description = "Invalid deployment data"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found")
    })
    @Parameter(name = "serviceProviderId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    public ResponseEntity<Participant> deployParticipant(@PathVariable Long serviceProviderId,
                                                         @PathVariable Long tenantId,
                                                         @PathVariable Long participantId,
                                                         @RequestBody ParticipantDeployment deployment) {
        var participant = tenantService.deployParticipant(deployment);
        return ResponseEntity.ok(participant);
    }

    @PostMapping("service-providers/{serviceProviderId}/tenants/{tenantId}/participants/{participantId}/dataplanes")
    public ResponseEntity<Void> registerDataPlane(@PathVariable Long serviceProviderId,
                                                  @PathVariable Long tenantId,
                                                  @PathVariable Long participantId) {
        tenantService.registerDataPlane(participantId, DataPlaneRegistrationRequest.ofDefault());
        return ResponseEntity.ok().build();
    }

    @GetMapping("service-providers/{serviceProviderId}/tenants/{tenantId}")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get tenant details", description = "Retrieves detailed information about a specific tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tenant details",
                    content = @Content(schema = @Schema(implementation = Tenant.class))),
            @ApiResponse(responseCode = "404", description = "Service provider or tenant not found")
    })
    @Parameter(name = "serviceProviderId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    public ResponseEntity<Tenant> getTenant(@PathVariable Long serviceProviderId,
                                            @PathVariable Long tenantId) {
        var tenantResource = tenantService.getTenant(tenantId);
        // TODO auth check for provider access
        return ResponseEntity.ok(tenantResource);
    }

    @GetMapping("service-providers/{serviceProviderId}/tenants/{tenantId}/participants/{participantId}")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get participant details", description = "Retrieves detailed information about a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved participant details",
                    content = @Content(schema = @Schema(implementation = Participant.class))),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found")
    })
    @Parameter(name = "serviceProviderId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    public ResponseEntity<Participant> getParticipant(@PathVariable Long serviceProviderId,
                                                      @PathVariable Long tenantId,
                                                      @PathVariable Long participantId) {
        var participantResource = tenantService.getParticipant(participantId);
        // TODO auth check for provider access
        return ResponseEntity.ok(participantResource);
    }

    @GetMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/partners/{dataspaceId}")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get partner references", description = "Retrieves a list of partner references for a participant in a specific dataspace")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved partner references"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, participant, or dataspace not found")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    @Parameter(name = "dataspaceId", description = "Database ID of the dataspace", required = true)
    public ResponseEntity<List<PartnerReference>> getPartners(@PathVariable Long providerId,
                                                              @PathVariable Long tenantId,
                                                              @PathVariable Long participantId,
                                                              @PathVariable Long dataspaceId) {
        var references = tenantService.getPartnerReferences(participantId, dataspaceId);
        // TODO auth check for provider access
        return ResponseEntity.ok(references);
    }

    @PostMapping("service-providers/{providerId}/tenants/{tenantId}/participants/{participantId}/partners/{dataspaceId}")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create partner reference", description = "Creates a new partner reference for a participant in a specific dataspace")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Partner reference successfully created",
                    content = @Content(schema = @Schema(implementation = PartnerReference.class))),
            @ApiResponse(responseCode = "400", description = "Invalid partner reference data"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, participant, or dataspace not found")
    })
    @Parameter(name = "providerId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    @Parameter(name = "dataspaceId", description = "Database ID of the dataspace", required = true)
    public ResponseEntity<PartnerReference> createPartner(@PathVariable Long providerId,
                                                          @PathVariable Long tenantId,
                                                          @PathVariable Long participantId,
                                                          @PathVariable Long dataspaceId,
                                                          @RequestBody PartnerReferenceRequest request) {
        var partnerReference = tenantService.createPartnerReference(providerId, tenantId, participantId, dataspaceId, request);
        // TODO auth check for provider access
        return ResponseEntity.ok(partnerReference);
    }

    @GetMapping("service-providers/{serviceProviderId}/tenants/{tenantId}/participants/{participantId}/dataspaces")
//    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get participant dataspaces", description = "Retrieves a list of dataspaces associated with a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved participant dataspaces"),
            @ApiResponse(responseCode = "404", description = "Service provider, tenant, or participant not found")
    })
    @Parameter(name = "serviceProviderId", description = "Database ID of the service provider", required = true)
    @Parameter(name = "tenantId", description = "Database ID of the tenant", required = true)
    @Parameter(name = "participantId", description = "Database ID of the participant", required = true)
    public ResponseEntity<List<DataspaceResponse>> getParticipantDataspaces(@PathVariable Long serviceProviderId,
                                                                            @PathVariable Long tenantId,
                                                                            @PathVariable Long participantId) {
        var dataspaces = tenantService.getParticipantDataspaces(participantId);
        // TODO auth check for provider access
        return ResponseEntity.ok(dataspaces);
    }

}
