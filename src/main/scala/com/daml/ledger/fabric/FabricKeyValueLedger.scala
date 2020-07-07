// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.fabric

import java.util.UUID

import akka.stream.Materializer
import com.daml.DAMLKVConnector
import com.daml.api.util.TimeProvider
import com.daml.ledger.participant.state.kvutils.api.KeyValueParticipantState
import com.daml.ledger.participant.state.kvutils.app.ReadWriteService
import com.daml.ledger.participant.state.v1.{LedgerId, ParticipantId}
import com.daml.lf.data.Ref
import com.daml.lf.engine.Engine
import com.daml.metrics.Metrics
import com.daml.platform.akkastreams.dispatcher.Dispatcher
import com.daml.resources.{Resource, ResourceOwner}

import scala.concurrent.ExecutionContext

object FabricKeyValueLedger {

  val DefaultTimeProvider: TimeProvider = TimeProvider.UTC
  class Owner(
      initialLedgerId: Option[LedgerId],
      participantId: ParticipantId,
      timeProvider: TimeProvider = DefaultTimeProvider,
      dispatcher: Dispatcher[Index],
      metrics: Metrics,
      engine: Engine
  )(implicit materializer: Materializer)
      extends ResourceOwner[ReadWriteService] {
    override def acquire()(
        implicit executionContext: ExecutionContext
    ): Resource[ReadWriteService] = {

      val ledgerId =
        initialLedgerId.getOrElse(Ref.LedgerString.assertFromString(UUID.randomUUID.toString))
      val reader =
        new FabricLedgerReaderWriter(
          participantId,
          ledgerId,
          timeProvider,
          metrics,
          dispatcher,
          engine
        )

      Resource.successful(
        new KeyValueParticipantState(reader, reader, metrics)
      )
    }
  }
}
