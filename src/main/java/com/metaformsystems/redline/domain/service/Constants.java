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
 *       Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V. - refactoring
 *
 */

package com.metaformsystems.redline.domain.service;

import com.metaformsystems.redline.infrastructure.client.management.dto.PolicySet;

public interface Constants {
    String ASSET_PERMISSION = "membership_asset";
    String MEMBERSHIP_EXPRESSION_ID = "membership_expr";
    String MEMBERSHIP_LEFT_OPERAND = "membershipStartDate";

    String MEMBERSHIP_EXPRESSION =
            "ctx.agent.claims.vc.exists(c, " +
            "  c.credentialSubject.exists(cs, " +
            "    cs.membershipStartDate != null && timestamp(cs.membershipStartDate) < now" +
            "  )" +
            ")";

    PolicySet.Constraint MEMBERSHIP_CONSTRAINT =
            new PolicySet.Constraint(MEMBERSHIP_LEFT_OPERAND, "eq", "active");
}