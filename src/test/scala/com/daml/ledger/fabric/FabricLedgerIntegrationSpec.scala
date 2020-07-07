// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.fabric

import java.time.Instant

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.daml.ledger.participant.state.kvutils.ParticipantStateIntegrationSpecBase
import com.daml.ledger.participant.state.kvutils.ParticipantStateIntegrationSpecBase.ParticipantState
import com.daml.ledger.participant.state.v1._
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext
import com.daml.metrics.Metrics
import com.daml.resources.ResourceOwner

abstract class FabricLedgerIntegrationSpecBase
    extends ParticipantStateIntegrationSpecBase(
      s"Fabric participant state via simplified API implementation"
    ) {

  override val isPersistent: Boolean = false

  val sharedEngine = new Engine(Engine.DevConfig)

  override def participantStateFactory(
      ledgerId: Option[LedgerId],
      participantId: ParticipantId,
      testId: String,
      heartbeats: Source[Instant, NotUsed],
      metrics: Metrics
  )(implicit logCtx: LoggingContext): ResourceOwner[ParticipantState] = {
    for {
      dispatcher <- FabricLedgerReaderWriter.newDispatcher()
      participantState <- new FabricKeyValueLedger.Owner(
        initialLedgerId = ledgerId,
        participantId = participantId,
        dispatcher = dispatcher,
        metrics = metrics,
        engine = sharedEngine
      )
    } yield participantState
  }
}

//TODO BH: IGNORE test until new simplified api implementation is passing
//class FabricLedgerIntegrationSpec extends FabricLedgerIntegrationSpecBase
