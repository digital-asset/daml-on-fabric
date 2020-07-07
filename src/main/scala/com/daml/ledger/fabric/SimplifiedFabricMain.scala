// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.fabric

import akka.stream.Materializer
import com.daml.DAMLKVConnector
import com.daml.ledger.participant.state.kvutils.app._
import com.daml.ledger.participant.state.v1.SeedService.Seeding
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext
import com.daml.logging.LoggingContext.newLoggingContext
import com.daml.platform.akkastreams.dispatcher.Dispatcher
import com.daml.platform.apiserver.ApiServerConfig
import com.daml.resources.{ProgramResource, ResourceOwner}
import org.slf4j.{Logger, LoggerFactory}
import scopt.OptionParser

import scala.concurrent.ExecutionContextExecutor

object SimplifiedFabricMain {
  //TODO BH: confirm if this is needed -- was giving delayedInit warning without
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def main(args: Array[String]): Unit = {

    val resourceOwner = for {
      dispatcher <- FabricLedgerReaderWriter.newDispatcher()
      factory = new FabricLedgerFactory(dispatcher)
      runner <- new Runner("daml-on-fabric", factory).owner(args)
    } yield runner
    new ProgramResource(resourceOwner).run()
  }

  final class FabricLedgerFactory(dispatcher: Dispatcher[Index])
      extends LedgerFactory[ReadWriteService, ExtraConfig] {
    override def readWriteServiceOwner(
        config: Config[ExtraConfig],
        participantConfig: ParticipantConfig,
        engine: Engine
    )(
        implicit materializer: Materializer,
        logCtx: LoggingContext
    ): ResourceOwner[ReadWriteService] = {

      newLoggingContext { implicit logCtx =>
        implicit val executionContext: ExecutionContextExecutor = materializer.executionContext

        // Initialize Fabric connection
        // this will create the singleton instance and establish the connection
        val fabricConn = DAMLKVConnector.get(config.extra.roleProvision, config.extra.roleExplorer)

        // If we only want to provision, exit right after
        if (!config.extra.roleLedger && !config.extra.roleTime && !config.extra.roleExplorer) {
          logger.info("Hyperledger Fabric provisioning complete.")
          System.exit(0)
        }

        val metrics = createMetrics(participantConfig, config)

        for {
          ledger <- new FabricKeyValueLedger.Owner(
            config.ledgerId,
            participantConfig.participantId,
            dispatcher = dispatcher,
            metrics = metrics,
            engine = engine
          )
        } yield ledger

      }
    }

    override val defaultExtraConfig: ExtraConfig = ExtraConfig.default

    override def extraConfigParser(parser: OptionParser[Config[ExtraConfig]]): Unit = {
      parser
        .opt[Int]("maxInboundMessageSize")
        .action((maxInboundMessageSize, config) => {
          config.copy(extra = config.extra.copy(maxInboundMessageSize = maxInboundMessageSize))
        })
        .text(
          s"Max inbound message size in bytes. Defaults to ${ExtraConfig.defaultMaxInboundMessageSize}."
        )

      parser
        .opt[String]("role")
        .text(
          "Role to start the application as. Supported values (may be multiple values separated by a comma):\n" +
            "                         ledger: run a Ledger API service\n" +
            "                         time: run a Time Service\n" +
            "                         provision: set up the chaincode if not present\n" +
            "                         explorer: run a Fabric Block Explorer API."
        )
        .required()
        .action((r, c) => {
          val splitStr = r.toLowerCase.split("\\s*,\\s*")
          c.copy(
            extra = c.extra.copy(
              roleLedger = splitStr.contains("ledger"),
              roleProvision = splitStr.contains("provision"),
              roleTime = splitStr.contains("time"),
              roleExplorer = splitStr.contains("explorer")
            )
          )
        })

      val seedingMap = Map[String, Option[Seeding]](
        "no" -> None,
        "testing-static" -> Some(Seeding.Static),
        "testing-weak" -> Some(Seeding.Weak),
        "strong" -> Some(Seeding.Strong)
      )

      parser
        .opt[String]("contract-id-seeding")
        .optional()
        .text(s"""Set the seeding of contract ids. Possible values are ${seedingMap.keys
          .mkString(",")}. Default is "no".""")
        .validate(
          v =>
            Either.cond(
              seedingMap.contains(v.toLowerCase),
              (),
              s"seeding must be ${seedingMap.keys.mkString(",")}"
            )
        )
        .action(
          (text, config) => config.copy(extra = config.extra.copy(seeding = seedingMap(text)))
        )
    }

    override def apiServerConfig(
        participantConfig: ParticipantConfig,
        config: Config[ExtraConfig]
    ): ApiServerConfig =
      super
        .apiServerConfig(participantConfig, config)
        .copy(maxInboundMessageSize = config.extra.maxInboundMessageSize)
  }
}
