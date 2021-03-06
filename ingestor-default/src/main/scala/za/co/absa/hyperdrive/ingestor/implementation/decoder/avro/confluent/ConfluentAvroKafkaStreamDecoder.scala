/*
 * Copyright 2018 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.hyperdrive.ingestor.implementation.decoder.avro.confluent

import org.apache.commons.configuration2.Configuration
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.LogManager
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.streaming.DataStreamReader
import za.co.absa.abris.avro.read.confluent.SchemaManager
import za.co.absa.abris.avro.read.confluent.SchemaManager.{PARAM_SCHEMA_NAMESPACE_FOR_RECORD_STRATEGY, PARAM_SCHEMA_NAME_FOR_RECORD_STRATEGY, PARAM_VALUE_SCHEMA_NAMING_STRATEGY, SchemaStorageNamingStrategies}
import za.co.absa.hyperdrive.ingestor.api.decoder.{StreamDecoder, StreamDecoderFactory}
import za.co.absa.hyperdrive.ingestor.implementation.utils.SchemaRegistrySettingsUtil
import za.co.absa.hyperdrive.shared.configurations.ConfigurationsKeys.AvroKafkaStreamDecoderKeys._
import za.co.absa.hyperdrive.shared.utils.ConfigUtils.getOrThrow

private[decoder] class ConfluentAvroKafkaStreamDecoder(val topic: String, val schemaRegistrySettings: Map[String,String]) extends StreamDecoder {

  if (StringUtils.isBlank(topic)) {
    throw new IllegalArgumentException("Blank topic.")
  }

  if (schemaRegistrySettings.isEmpty) {
    throw new IllegalArgumentException("Empty Schema Registry settings received.")
  }

  private val logger = LogManager.getLogger

  override def decode(streamReader: DataStreamReader): DataFrame = {
    val schemaRegistryFullSettings = schemaRegistrySettings + (SchemaManager.PARAM_SCHEMA_REGISTRY_TOPIC -> topic)
    logger.info(s"SchemaRegistry settings: $schemaRegistryFullSettings")

    import org.apache.spark.sql.functions.col
    import za.co.absa.abris.avro.functions.from_confluent_avro
    streamReader
      .load()
      .select(from_confluent_avro(col("value"), schemaRegistryFullSettings) as 'data)
      .select("data.*")
  }
}

object ConfluentAvroKafkaStreamDecoder extends StreamDecoderFactory with ConfluentAvroKafkaStreamDecoderAttributes {

  override def apply(config: Configuration): StreamDecoder = {
    val topic = getTopic(config)
    val schemaRegistrySettings = getSchemaRegistrySettings(config)

    LogManager.getLogger.info(s"Going to create AvroKafkaStreamDecoder instance using: topic='$topic', schema registry settings='$schemaRegistrySettings'.")

    new ConfluentAvroKafkaStreamDecoder(topic, schemaRegistrySettings)
  }

  private def getTopic(configuration: Configuration): String = getOrThrow(KEY_TOPIC, configuration, errorMessage = s"Topic not found. Is '$KEY_TOPIC' properly set?")

  private def getSchemaRegistrySettings(configuration: Configuration): Map[String, String] = {
    import SchemaManager._
    val settings = Map[String, String](
      PARAM_SCHEMA_REGISTRY_URL -> getOrThrow(KEY_SCHEMA_REGISTRY_URL, configuration, errorMessage = s"Schema Registry URL not specified. Is '$KEY_SCHEMA_REGISTRY_URL' configured?"),
      PARAM_VALUE_SCHEMA_ID -> getOrThrow(KEY_SCHEMA_REGISTRY_VALUE_SCHEMA_ID, configuration, errorMessage = s"Schema id not specified for value. Is '$KEY_SCHEMA_REGISTRY_VALUE_SCHEMA_ID' configured?"),
      PARAM_VALUE_SCHEMA_NAMING_STRATEGY -> getOrThrow(KEY_SCHEMA_REGISTRY_VALUE_NAMING_STRATEGY, configuration, errorMessage = s"Schema naming strategy not specified for value. Is '$KEY_SCHEMA_REGISTRY_VALUE_NAMING_STRATEGY' configured?")
    )

    settings ++ getRecordSettings(settings, configuration)
  }

  private def getRecordSettings(currentSettings: Map[String, String], configuration: Configuration): Map[String, String] = {
    val valueNamingStrategy = currentSettings(PARAM_VALUE_SCHEMA_NAMING_STRATEGY)

    if (SchemaRegistrySettingsUtil.namingStrategyInvolvesRecord(valueNamingStrategy)) {
      Map(
        PARAM_SCHEMA_NAME_FOR_RECORD_STRATEGY -> getOrThrow(KEY_SCHEMA_REGISTRY_VALUE_RECORD_NAME, configuration, errorMessage = s"Record name not specified for value. Is '$KEY_SCHEMA_REGISTRY_VALUE_RECORD_NAME' configured?"),
        PARAM_SCHEMA_NAMESPACE_FOR_RECORD_STRATEGY -> getOrThrow(KEY_SCHEMA_REGISTRY_VALUE_RECORD_NAMESPACE, configuration, errorMessage = s"Record namespace not specified for value. Is '$KEY_SCHEMA_REGISTRY_VALUE_RECORD_NAMESPACE' configured?")
      )
    } else {
      Map()
    }
  }
}
