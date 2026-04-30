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

package com.metaformsystems.redline.infrastructure.client.tenantmanager.v1alpha1.dto;

import java.util.List;
import java.util.Map;

public record ParticipantProfile(
        String id,
        Long version,
        String identifier,
        String tenantId,
        Boolean error,
        String errorDetail,
        List<String> dataspaceProfileIds,
        Map<String, List<String>> participantRoles,
        Map<String, Object> properties,
        List<VirtualParticipantAgent> vpas
) {
}
