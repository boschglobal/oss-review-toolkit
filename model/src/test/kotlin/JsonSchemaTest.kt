/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.model

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.serialization.JsonNodeReader

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATION_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REFERENCE_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.test.readResource
import org.ossreviewtoolkit.utils.test.readResourceValue

class JsonSchemaTest : StringSpec({
    "ORT's own repository configuration file validates successfully" {
        val repositoryConfiguration = File("../$ORT_REPO_CONFIG_FILENAME").toJsonNode()

        val errors = schemaV7.getSchema(repositoryConfigurationSchema).validate(repositoryConfiguration)

        errors should beEmpty()
    }

    "The example ORT repository configuration file validates successfully" {
        val examplesDir = File("../examples")
        val exampleFiles =
            examplesDir.walk().filterTo(mutableListOf()) { it.isFile && it.name.endsWith(ORT_REPO_CONFIG_FILENAME) }

        exampleFiles.forAll {
            val repositoryConfiguration = it.toJsonNode()

            val errors = schemaV7.getSchema(repositoryConfigurationSchema).validate(repositoryConfiguration)

            errors should beEmpty()
        }
    }

    "Analyzer configuration within a repository configuration validates successfully" {
        val analyzerConfiguration = readResourceValue<RepositoryConfiguration>(
            "/analyzer-repository-configuration.ort.yml"
        ).analyzer

        val errors = schemaV7.getSchema(repositoryConfigurationAnalyzerConfiguration)
            .validate(analyzerConfiguration.toYaml(), InputFormat.YAML)

        errors should beEmpty()
    }

    "Package manager configuration within a repository configuration validates successfully" {
        val packageManagerConfiguration = readResourceValue<RepositoryConfiguration>(
            "/package-manager-repository-configuration.ort.yml"
        ).analyzer?.packageManagers

        val errors = schemaV7.getSchema(repositoryConfigurationPackageManagerConfiguration)
            .validate(packageManagerConfiguration.toYaml(), InputFormat.YAML)

        errors should beEmpty()
    }

    "The example package curations file validates successfully" {
        val curationsSchema = File("../integrations/schemas/curations-schema.json").toURI()
        val curationsExample = File("../examples/$ORT_PACKAGE_CURATIONS_FILENAME").toJsonNode()

        val errors = schemaV7.getSchema(curationsSchema).validate(curationsExample)

        errors should beEmpty()
    }

    "The example package configuration file validates successfully" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json").toURI()
        val packageConfiguration = File("../examples/$ORT_PACKAGE_CONFIGURATION_FILENAME").toJsonNode()

        val errors = schemaV7.getSchema(packageConfigurationSchema).validate(packageConfiguration)

        errors should beEmpty()
    }

    "The example resolutions file validates successfully" {
        val resolutionsSchema = File("../integrations/schemas/resolutions-schema.json").toURI()
        val resolutionsExample = File("../examples/$ORT_RESOLUTIONS_FILENAME").toJsonNode()

        val errors = schemaV7.getSchema(resolutionsSchema).validate(resolutionsExample)

        errors should beEmpty()
    }

    "The embedded reference configuration validates successfully" {
        val ortConfigurationSchema = File("../integrations/schemas/ort-configuration-schema.json").toURI()
        val referenceConfigFile = File("src/main/resources/$ORT_REFERENCE_CONFIG_FILENAME").toJsonNode()

        val errors = schemaV7.getSchema(ortConfigurationSchema).validate(referenceConfigFile)

        errors should beEmpty()
    }

    "The example license classifications file validates successfully" {
        val licenseClassificationsSchema = File("../integrations/schemas/license-classifications-schema.json").toURI()
        val licenseClassificationsExample = File("../examples/$ORT_LICENSE_CLASSIFICATIONS_FILENAME").toJsonNode()

        val errors = schemaV7.getSchema(licenseClassificationsSchema).validate(licenseClassificationsExample)

        errors should beEmpty()
    }

    "Snippet choices validate successfully" {
        val repositoryConfiguration = readResource("/snippet-choices-repository-configuration.ort.yml")

        val errors = schemaV7.getSchema(repositoryConfigurationSchema)
            .validate(repositoryConfiguration, InputFormat.YAML)

        errors should beEmpty()
    }
})

private val nodeReader = JsonNodeReader.builder().yamlMapper(yamlMapper).build()

private val schemaV7 = JsonSchemaFactory
    .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
    .jsonNodeReader(nodeReader)
    .build()

private val repositoryConfigurationSchema =
    File("../integrations/schemas/repository-configuration-schema.json").toURI()

private val repositoryConfigurationAnalyzerConfiguration =
    File("../integrations/schemas/repository-configurations/analyzer-configuration-schema.json").toURI()

private val repositoryConfigurationPackageManagerConfiguration =
    File("../integrations/schemas/repository-configurations/package-manager-configuration-schema.json").toURI()

private fun File.toJsonNode() = yamlMapper.readTree(inputStream())
