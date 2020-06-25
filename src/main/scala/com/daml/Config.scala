// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml

import java.io.File
import java.nio.file.Path

import com.daml.ledger.participant.state.v1
import com.daml.ledger.participant.state.v1.ParticipantId
import com.daml.api.util.TimeProvider
import com.daml.ledger.api.tls.TlsConfiguration
import com.daml.ledger.participant.state.v1.SeedService.Seeding
import com.daml.platform.indexer.IndexerStartupMode
import com.daml.ports.Port
import com.daml.ledger.api.auth.{AuthService, AuthServiceWildcard}
import com.daml.platform.configuration.IndexConfiguration

final case class Config(
    port: Port,
    portFile: Option[File],
    archiveFiles: List[Path],
    maxInboundMessageSize: Int,
    eventsPageSize: Int,
    stateValueCache: caching.WeightedCache.Configuration,
    lfValueTranslationEventCacheConfiguration: caching.SizedCache.Configuration,
    lfValueTranslationContractCacheConfiguration: caching.SizedCache.Configuration,
    timeProvider: TimeProvider,
    address: Option[String],
    jdbcUrl: String,
    tlsConfig: Option[TlsConfiguration],
    participantId: v1.ParticipantId,
    startupMode: IndexerStartupMode,
    roleLedger: Boolean,
    roleTime: Boolean,
    roleProvision: Boolean,
    roleExplorer: Boolean,
    authService: AuthService,
    seeding: Seeding
) {
  def withTlsConfig(modify: TlsConfiguration => TlsConfiguration): Config =
    copy(tlsConfig = Some(modify(tlsConfig.getOrElse(TlsConfiguration.Empty))))
}

object Config {
  val DefaultMaxInboundMessageSize = 4194304

  def default: Config =
    new Config(
      port = Port(0),
      portFile = None,
      archiveFiles = List.empty,
      maxInboundMessageSize = DefaultMaxInboundMessageSize,
      eventsPageSize = IndexConfiguration.DefaultEventsPageSize,
      stateValueCache = caching.WeightedCache.Configuration.none,
      lfValueTranslationEventCacheConfiguration = caching.SizedCache.Configuration.none,
      lfValueTranslationContractCacheConfiguration = caching.SizedCache.Configuration.none,
      timeProvider = TimeProvider.UTC,
      address = Some("0.0.0.0"),
      jdbcUrl = "jdbc:h2:mem:daml_on_fabric;db_close_delay=-1;db_close_on_exit=false",
      tlsConfig = None,
      participantId = ParticipantId.assertFromString("fabric-standalone-participant"),
      startupMode = IndexerStartupMode.MigrateAndStart,
      roleLedger = false,
      roleTime = false,
      roleProvision = false,
      roleExplorer = false,
      authService = AuthServiceWildcard,
      seeding = Seeding.Weak
    )
}
