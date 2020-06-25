// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml

import java.io.File

import com.auth0.jwt.algorithms.Algorithm
import com.daml.jwt.{ECDSAVerifier, HMAC256Verifier, JwksVerifier, RSA256Verifier}
import com.daml.ledger.api.auth.AuthServiceJWT
import com.daml.ledger.api.tls.TlsConfiguration
import com.daml.ledger.participant.state.v1.ParticipantId
import com.daml.ledger.participant.state.v1.SeedService.Seeding
import com.daml.lf.data.Ref
import com.daml.platform.configuration.IndexConfiguration
import com.daml.ports.Port
import scopt.{OptionParser, Read}

object Cli {

  implicit private val ledgerStringRead: Read[Ref.LedgerString] =
    Read.stringRead.map(Ref.LedgerString.assertFromString)

  implicit private def tripleRead[A, B, C](
      implicit readA: Read[A],
      readB: Read[B],
      readC: Read[C]
  ): Read[(A, B, C)] =
    Read.seqRead[String].map {
      case Seq(a, b, c) => (readA.reads(a), readB.reads(b), readC.reads(c))
      case a            => throw new RuntimeException(s"Expected a comma-separated triple, got '$a'")
    }

  private val pemConfig = (path: String, config: Config) =>
    config.copy(
      tlsConfig = config.tlsConfig.fold(
        Some(TlsConfiguration(enabled = true, None, Some(new File(path)), None))
      )(c => Some(c.copy(keyFile = Some(new File(path)))))
    )

  private val crtConfig = (path: String, config: Config) =>
    config.copy(
      tlsConfig = config.tlsConfig.fold(
        Some(TlsConfiguration(enabled = true, Some(new File(path)), None, None))
      )(c => Some(c.copy(keyCertChainFile = Some(new File(path)))))
    )

  private val cacrtConfig = (path: String, config: Config) =>
    config.copy(
      tlsConfig = config.tlsConfig.fold(
        Some(TlsConfiguration(enabled = true, None, None, Some(new File(path))))
      )(c => Some(c.copy(trustCertCollectionFile = Some(new File(path)))))
    )

  private def cmdArgParser(
      binaryName: String,
      description: String,
      allowExtraParticipants: Boolean
  ): OptionParser[Config] =
    new scopt.OptionParser[Config](binaryName) {
      head(description)
      opt[Int]("port")
        .optional()
        .action((p, c) => c.copy(port = Port(p)))
        .text("Server port. If not set, a random port is allocated.")
      opt[File]("port-file")
        .optional()
        .action((f, c) => c.copy(portFile = Some(f)))
        .text(
          "File to write the allocated port number to. Used to inform clients in CI about the allocated port."
        )
      opt[String]("pem")
        .optional()
        .text("TLS: The pem file to be used as the private key.")
        .action(pemConfig)
      opt[String]("crt")
        .optional()
        .text(
          "TLS: The crt file to be used as the cert chain. Required if any other TLS parameters are set."
        )
        .action(crtConfig)
      opt[String]("cacrt")
        .optional()
        .text("TLS: The crt file to be used as the the trusted root CA.")
        .action(cacrtConfig)
      opt[Int]("maxInboundMessageSize")
        .action((x, c) => c.copy(maxInboundMessageSize = x))
        .text(
          s"Max inbound message size in bytes. Defaults to ${Config.DefaultMaxInboundMessageSize}."
        )
      opt[String]("address")
        .optional()
        .action((a, c) => c.copy(address = Some(a)))
        .text(
          s"Address that the ledger api server will bind to when started.  If not specified defaults to 0.0.0.0"
        )
      opt[String]("jdbc-url")
        .text("The JDBC URL to the postgres database used for the indexer and the index")
        .action((u, c) => c.copy(jdbcUrl = u))
      opt[String]("participant-id")
        .optional()
        .text("The participant id given to all components of a ledger api server")
        .action((p, c) => c.copy(participantId = ParticipantId.assertFromString(p)))
      opt[String]("role")
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
            roleLedger = splitStr.contains("ledger"),
            roleProvision = splitStr.contains("provision"),
            roleTime = splitStr.contains("time"),
            roleExplorer = splitStr.contains("explorer")
          )
        })
      opt[String]("auth-jwt-hs256-unsafe")
        .optional()
        .hidden()
        .validate(v => Either.cond(v.length > 0, (), "HMAC secret must be a non-empty string"))
        .text(
          "[UNSAFE] Enables JWT-based authorization with shared secret HMAC256 signing: USE THIS EXCLUSIVELY FOR TESTING"
        )
        .action(
          (secret, c) =>
            c.copy(
              authService = AuthServiceJWT(
                HMAC256Verifier(secret)
                  .valueOr(err => sys.error(s"Failed to create HMAC256 verifier: $err"))
              )
            )
        )
      opt[String]("auth-jwt-rs256-crt")
        .optional()
        .validate(
          v => Either.cond(v.length > 0, (), "Certificate file path must be a non-empty string")
        )
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given X509 certificate file (.crt)"
        )
        .action(
          (path, c) =>
            c.copy(
              authService = AuthServiceJWT(
                RSA256Verifier
                  .fromCrtFile(path)
                  .valueOr(err => sys.error(s"Failed to create RSA256 verifier: $err"))
              )
            )
        )
      opt[String]("auth-jwt-es256-crt")
        .optional()
        .validate(
          v => Either.cond(v.length > 0, (), "Certificate file path must be a non-empty string")
        )
        .text(
          "Enables JWT-based authorization, where the JWT is signed by ECDSA256 with a public key loaded from the given X509 certificate file (.crt)"
        )
        .action(
          (path, c) =>
            c.copy(
              authService = AuthServiceJWT(
                ECDSAVerifier
                  .fromCrtFile(path, Algorithm.ECDSA256(_, null))
                  .valueOr(err => sys.error(s"Failed to create ECDSA256 verifier: $err"))
              )
            )
        )
      opt[String]("auth-jwt-es512-crt")
        .optional()
        .validate(
          v => Either.cond(v.length > 0, (), "Certificate file path must be a non-empty string")
        )
        .text(
          "Enables JWT-based authorization, where the JWT is signed by ECDSA512 with a public key loaded from the given X509 certificate file (.crt)"
        )
        .action(
          (path, c) =>
            c.copy(
              authService = AuthServiceJWT(
                ECDSAVerifier
                  .fromCrtFile(path, Algorithm.ECDSA512(_, null))
                  .valueOr(err => sys.error(s"Failed to create ECDSA512 verifier: $err"))
              )
            )
        )
      opt[String]("auth-jwt-rs256-jwks")
        .optional()
        .validate(v => Either.cond(v.length > 0, (), "JWK server URL must be a non-empty string"))
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given JWKS URL"
        )
        .action((url, c) => c.copy(authService = AuthServiceJWT(JwksVerifier(url))))

      opt[Int]("events-page-size")
        .optional()
        .text(
          s"Number of events fetched from the index for every round trip when serving streaming calls. Default is ${IndexConfiguration.DefaultEventsPageSize}."
        )
        .action((eventsPageSize, config) => config.copy(eventsPageSize = eventsPageSize))

      opt[Long]("max-state-value-cache-size")
        .optional()
        .text(
          s"The maximum size of the cache used to deserialize state values, in MB. By default, nothing is cached."
        )
        .action(
          (maximumStateValueCacheSize, config) =>
            config.copy(
              stateValueCache = config.stateValueCache
                .copy(maximumWeight = maximumStateValueCacheSize * 1024 * 1024)
            )
        )

      opt[Long]("max-lf-value-translation-cache-entries")
        .optional()
        .text(
          s"The maximum size of the cache used to deserialize DAML-LF values, in number of allowed entries. By default, nothing is cached."
        )
        .action(
          (maximumLfValueTranslationCacheEntries, config) =>
            config.copy(
              lfValueTranslationEventCacheConfiguration =
                config.lfValueTranslationEventCacheConfiguration
                  .copy(maximumSize = maximumLfValueTranslationCacheEntries),
              lfValueTranslationContractCacheConfiguration =
                config.lfValueTranslationContractCacheConfiguration
                  .copy(maximumSize = maximumLfValueTranslationCacheEntries)
            )
        )

      private val seedingTypeMap = Map[String, Seeding](
        "testing-static" -> Seeding.Static,
        "testing-weak" -> Seeding.Weak,
        "strong" -> Seeding.Strong
      )

      opt[String]("contract-id-seeding")
        .optional()
        .text(s"""Set the seeding of contract ids. Possible values are ${seedingTypeMap.keys
          .mkString(",")}. Default is "testing-weak".""")
        .validate(
          v =>
            Either.cond(
              seedingTypeMap.contains(v.toLowerCase),
              (),
              s"seeding must be ${seedingTypeMap.keys.mkString(",")}"
            )
        )
        .action((text, config) => config.copy(seeding = seedingTypeMap(text)))

      arg[File]("<archive>...")
        .optional()
        .unbounded()
        .text(
          "DAR files to load. Scenarios are ignored. The server starts with an empty ledger by default."
        )
        .action((file, config) => config.copy(archiveFiles = config.archiveFiles :+ file.toPath))
    }

  def parse(
      args: Array[String],
      binaryName: String,
      description: String,
      allowExtraParticipants: Boolean = false
  ): Option[Config] =
    cmdArgParser(binaryName, description, allowExtraParticipants).parse(args, Config.default)
}
