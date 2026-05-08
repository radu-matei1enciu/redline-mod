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

package com.metaformsystems.redline.domain.repository;

import com.metaformsystems.redline.domain.entity.Dataspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataspaceRepository extends JpaRepository<Dataspace, Long> {
    @Query("""
            SELECT d FROM Dataspace d
            WHERE d.id IN (
                SELECT di.dataspaceId FROM DataspaceInfo di
                JOIN di.partners p
                WHERE p.identifier = :counterPartyIdentifier
            )
            """)
    List<Dataspace> findDataspacesByCounterPartyIdentifier(@Param("counterPartyIdentifier") String counterPartyIdentifier);

    @Query("""
    SELECT d
    FROM Dataspace d
    WHERE d.id IN (
        SELECT di.dataspaceId
        FROM DataspaceInfo di
        WHERE di IN (
            SELECT info FROM Participant p JOIN p.dataspaceInfos info
            WHERE p.id = :participantId
        )
    )
""")
    List<Dataspace> findDataspacesByParticipantId(@Param("participantId") Long participantId);
}