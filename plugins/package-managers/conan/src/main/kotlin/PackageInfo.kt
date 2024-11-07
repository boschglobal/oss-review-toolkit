/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import kotlinx.serialization.KSerializer
import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


private val JSON = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
    coerceInputValues = true
}

internal fun parsePackageInfos(file: File): List<PackageInfo> = JSON.decodeFromString(file.readText())

internal fun parsePackageInfosV2(file: File): List<PackageInfo> =
    JSON.decodeFromString<PackageInfoV2>(file.readText()).toPackageInfos()

@Serializable
internal data class PackageInfo(
    val reference: String? = null,
    val author: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val revision: String? = null,
    val url: String? = null,
    val displayName: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList(),
    // Conan 2 only
    val recipeFolder: String? = null
)

@Serializable
internal data class PackageInfoV2(
    val graph: Graph
) {
    fun toPackageInfos() = graph.nodes.values.map {
        PackageInfo(
            reference = it.ref,
            author = it.author,
            license = it.license,
            homepage = it.homepage,
            url = it.url,
            displayName = it.label,
            requires = it.info?.requires ?: emptyList(),
            recipeFolder = it.recipeFolder
        )
    }
}

@Serializable
internal data class Graph (
    val nodes: Map<String, PackageV2>
)

@Serializable
internal data class PackageV2(
    val ref: String,
    val author: String? = null,
    @Serializable(with = StringListSerializer::class)
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val url: String? = null,
    val label: String,
    val info: InfoV2? = null,
    val recipeFolder: String? = null // Null for the first package, i.e. the conanfile itself.
)

@Serializable
internal data class InfoV2(
    val requires: List<String> = emptyList()
)

/**
 * A (de)serialized for the list of licenses: the JSON can contain either null, string or an array of string for this
 * property.
 */
object StringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StringList") {
        element("value", listSerialDescriptor<String>())
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> element.jsonArray.map { it.jsonPrimitive.content }
            is JsonPrimitive -> listOfNotNull(element.contentOrNull)
            else -> emptyList()
        }
    }
}
