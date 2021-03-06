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

package za.co.absa.hyperdrive.ingestor.implementation.manager.factories

import org.apache.commons.configuration2.Configuration
import org.mockito.Mockito.{when, _}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FlatSpec}
import za.co.absa.hyperdrive.ingestor.implementation.manager.checkpoint.CheckpointOffsetManager
import za.co.absa.hyperdrive.shared.configurations.ConfigurationsKeys.CheckpointOffsetManagerKeys._

class TestStreamManagerAbstractFactory extends FlatSpec with BeforeAndAfterEach with MockitoSugar {

  private val configStub = mock[Configuration]

  behavior of StreamManagerAbstractFactory.getClass.getSimpleName

  override def beforeEach(): Unit = reset(configStub)

  it should "create CheckpointOffsetManager" in {
    when(configStub.getString(StreamManagerAbstractFactory.componentConfigKey)).thenReturn(CheckpointOffsetManager.getClass.getName)
    when(configStub.getString(KEY_CHECKPOINT_BASE_LOCATION)).thenReturn("/tmp/checkpoint")
    assert(StreamManagerAbstractFactory.build(configStub).isInstanceOf[CheckpointOffsetManager])
  }

  it should "throw if manager parameter is invalid" in {
    val invalidFactoryName = "an-invalid-factory-name"
    when(configStub.getString(StreamManagerAbstractFactory.componentConfigKey)).thenReturn(invalidFactoryName)
    val throwable = intercept[IllegalArgumentException](StreamManagerAbstractFactory.build(configStub))
    assert(throwable.getMessage.contains(invalidFactoryName))
  }

  it should "throw if offset manager parameter is absent" in {
    assertThrows[IllegalArgumentException](StreamManagerAbstractFactory.build(configStub))
  }
}
