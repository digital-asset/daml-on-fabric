// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml

import java.nio.file.Path
import java.time.Duration
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.codahale.metrics.SharedMetricRegistries
import com.daml.daml_lf_dev.DamlLf.Archive
import com.daml.ledger.participant.state.v1.{
  Configuration,
  SubmissionId,
  TimeModel,
  WritePackagesService
}
import com.daml.lf.archive.DarReader
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext.newLoggingContext
import com.daml.metrics.{JvmMetricSet, Metrics}
import com.daml.platform.apiserver.{ApiServerConfig, StandaloneApiServer, TimedIndexService}
import com.daml.platform.configuration.{
  CommandConfiguration,
  LedgerConfiguration,
  PartyConfiguration
}
import com.daml.platform.indexer.{IndexerConfig, StandaloneIndexerServer}
import com.daml.platform.store.dao.events.LfValueTranslation
import com.daml.resources.akka.AkkaResourceOwner
import com.daml.resources.{ProgramResource, Resource, ResourceOwner}
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** The example server is a fully compliant DAML Ledger API server
  * backed by the H2 in-memory db reference index and participant state implementations.
  * Not meant for production
  */
object DamlOnFabricServer extends App {
  val logger = LoggerFactory.getLogger(this.getClass)

  val config: com.daml.Config = Cli
    .parse(
      args,
      "daml-on-fabric",
      "A fully compliant DAML Ledger API server backed by Fabric"
    )
    .getOrElse(sys.exit(1))

  private val metricsRegistry =
    SharedMetricRegistries.getOrCreate(s"ledger-api-server-${config.participantId}")
  private val metrics = new Metrics(metricsRegistry)

  // Initialize Fabric connection
  // this will create the singleton instance and establish the connection
  val fabricConn = DAMLKVConnector.get(config.roleProvision, config.roleExplorer)

  // If we only want to provision, exit right after
  if (!config.roleLedger && !config.roleTime && !config.roleExplorer) {
    logger.info("Hyperledger Fabric provisioning complete.")
    System.exit(0)
  }

  // Initialize Akka and log exceptions in flows.
  val authService = config.authService

  def owner(): ResourceOwner[Unit] = new ResourceOwner[Unit] {
    override def acquire()(implicit executionContext: ExecutionContext): Resource[Unit] = {

      implicit val actorSystem: ActorSystem = ActorSystem("DamlonFabricServer")
      implicit val materializer: Materializer = Materializer(actorSystem)

      // DAML Engine for transaction validation.
      val sharedEngine = new Engine(Engine.StableConfig)

      newLoggingContext { implicit logCtx =>
        for {
          // Take ownership of the actor system and materializer so they're cleaned up properly.
          // This is necessary because we can't declare them as implicits in a `for` comprehension.
          _ <- AkkaResourceOwner.forActorSystem(() => actorSystem).acquire()
          _ <- AkkaResourceOwner.forMaterializer(() => materializer).acquire()

          // initialize all configured participants
          _ <- {
            metricsRegistry.registerAll(new JvmMetricSet)
            val lfValueTranslationCache = LfValueTranslation.Cache.newInstrumentedInstance(
              eventConfiguration = config.lfValueTranslationEventCacheConfiguration,
              contractConfiguration = config.lfValueTranslationContractCacheConfiguration,
              metrics = metrics
            )
            for {
              ledger <- ResourceOwner
                .forCloseable(
                  () =>
                    new FabricParticipantState(
                      config.roleTime,
                      config.roleLedger,
                      config.participantId,
                      metrics,
                      sharedEngine
                    )
                )
                .acquire() if config.roleLedger
              _ <- Resource.fromFuture(
                Future.sequence(config.archiveFiles.map(uploadDar(_, ledger)))
              ) if config.roleLedger
              _ <- new StandaloneIndexerServer(
                readService = ledger,
                config = IndexerConfig(config.participantId, config.jdbcUrl, config.startupMode),
                metrics = metrics,
                lfValueTranslationCache = lfValueTranslationCache
              ).acquire() if config.roleLedger
              _ <- new StandaloneApiServer(
                config = ApiServerConfig(
                  participantId = config.participantId,
                  archiveFiles = config.archiveFiles.map(_.toFile),
                  port = config.port,
                  address = config.address,
                  jdbcUrl = config.jdbcUrl,
                  tlsConfig = config.tlsConfig,
                  maxInboundMessageSize = config.maxInboundMessageSize,
                  eventsPageSize = config.eventsPageSize,
                  portFile = config.portFile.map(_.toPath),
                  seeding = config.seeding
                ),
                commandConfig = CommandConfiguration.default,
                partyConfig = PartyConfiguration(false),
                ledgerConfig = LedgerConfiguration(
                  initialConfiguration = Configuration(
                    generation = 1,
                    timeModel = TimeModel(
                      avgTransactionLatency = Duration.ofSeconds(0L),
                      minSkew = Duration.ofMinutes(2),
                      //TODO BH: temporarily give big tolerance on time while ensuring proper way of ticking it
                      maxSkew = Duration.ofMinutes(2)
                    ).get,
                    maxDeduplicationTime = Duration.ofDays(1)
                  ),
                  initialConfigurationSubmitDelay = Duration.ofSeconds(5),
                  configurationLoadTimeout = Duration.ofSeconds(30)
                ),
                readService = ledger,
                writeService = ledger,
                authService = authService,
                transformIndexService = service =>
                  new TimedIndexService(
                    service,
                    metrics
                  ),
                metrics = metrics,
                engine = sharedEngine,
                lfValueTranslationCache = lfValueTranslationCache
              ).acquire() if config.roleLedger
            } yield ()
          }
        } yield ()
      }
    }
  }

  private def uploadDar(from: Path, to: WritePackagesService)(
      implicit executionContext: ExecutionContext
  ): Future[Unit] = {
    val submissionId = SubmissionId.assertFromString(UUID.randomUUID().toString)
    for {
      dar <- Future(
        DarReader { case (_, x) => Try(Archive.parseFrom(x)) }.readArchiveFromFile(from.toFile).get
      )
      _ <- to.uploadPackages(submissionId, dar.all, None).toScala
    } yield ()
  }
  new ProgramResource(owner()).run()
}
