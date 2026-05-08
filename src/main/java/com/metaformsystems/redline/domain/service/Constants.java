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

public interface Constants {
    String DCP_SCOPE_TEMPLATE = "org.eclipse.dspace.dcp.vc.type:%s:read";
    String ASSET_PERMISSION = "membership_asset";

    String MEMBERSHIP_EXPRESSION_ID_TEMPLATE = "%s_expr_id";
    String MEMBERSHIP_EXPRESSION_TEMPLATE = "ctx.agent.claims.vc.filter(c, c.type.exists(t, t == '%s')).exists(c, c.credentialSubject.exists(cs, timestamp(cs.membershipStartDate) < now))";

    String MEMBERSHIP_CONSTRAINT_OPERATOR = "eq";
    String MEMBERSHIP_CONSTRAINT_RIGHT_OPERAND = "active";
}