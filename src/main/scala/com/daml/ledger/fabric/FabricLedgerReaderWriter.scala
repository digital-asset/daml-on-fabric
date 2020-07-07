// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.fabric

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.daml.DAMLKVConnector
import com.daml.api.util.TimeProvider
import com.daml.ledger.api.health.HealthStatus
import com.daml.ledger.participant.state.kvutils.Bytes
import com.daml.ledger.participant.state.kvutils.api.{LedgerReader, LedgerRecord, LedgerWriter}
import com.daml.ledger.participant.state.v1.{LedgerId, Offset, ParticipantId, SubmissionResult}
import com.daml.ledger.validator.{SubmissionValidator, ValidatingCommitter}
import com.daml.lf.engine.Engine
import com.daml.metrics.Metrics
import com.daml.platform.akkastreams.dispatcher.Dispatcher
import com.daml.resources.ResourceOwner

import scala.concurrent.{ExecutionContext, Future}

class FabricLedgerReaderWriter(
    override val participantId: ParticipantId,
    ledgerId: LedgerId,
    timeProvider: TimeProvider,
    metrics: Metrics,
    dispatcher: Dispatcher[Index],
    engine: Engine
)(implicit executionContext: ExecutionContext)
    extends LedgerReader
    with LedgerWriter {
  val fabricConn: DAMLKVConnector = DAMLKVConnector.get

  override def events(startExclusive: Option[Offset]): Source[LedgerRecord, NotUsed] =
    // TODO BH: implement me
    Source.empty

  override def ledgerId(): LedgerId = ledgerId

  override def currentHealth(): HealthStatus = HealthStatus.healthy

  val committer = new ValidatingCommitter(
    () => timeProvider.getCurrentTime,
    SubmissionValidator
      .create(
        ledgerStateAccess = new FabricLedgerStateAccess(),
        engine = engine,
        metrics = metrics
      ),
    dispatcher.signalNewHead
  )

  override def commit(correlationId: String, envelope: Bytes): Future[SubmissionResult] =
    committer.commit(correlationId, envelope, participantId)
}

object FabricLedgerReaderWriter {
  def newDispatcher(): ResourceOwner[Dispatcher[Index]] =
    ResourceOwner.forCloseable(
      () =>
        Dispatcher(
          "fabric-key-value-participant-state",
          zeroIndex = StartIndex,
          headAtInitialization = StartIndex
        )
    )
}
