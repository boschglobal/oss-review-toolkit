/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import java.io.File
import java.lang.invoke.MethodHandles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import net.peanuuutz.tomlkt.decodeFromNativeReader

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.normalizePythonVersion
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toml

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private const val UV_LOCKFILE_NAME = "uv.lock"
private const val PDM_LOCKFILE_NAME = "pdm.lock"
private const val POETRY_LOCKFILE_NAME = "poetry.lock"

/** The name of the implicit dependency group of Poetry that holds the runtime dependencies. */
private const val POETRY_MAIN_GROUP = "main"

/**
 * The parts of a `uv.lock` file that are needed to determine the packages a PEP 751 export of it must contain. Unlike
 * the other formats, uv records no group membership per package, but a dependency graph starting at the project.
 */
@Serializable
private data class UvLockfile(
    @SerialName("package")
    val packages: List<Package> = emptyList()
) {
    @Serializable
    data class Package(
        val name: String,
        val version: String? = null,
        val source: Map<String, String> = emptyMap(),
        val dependencies: List<Dependency> = emptyList(),

        @SerialName("optional-dependencies")
        val optionalDependencies: Map<String, List<Dependency>> = emptyMap(),

        @SerialName("dev-dependencies")
        val devDependencies: Map<String, List<Dependency>> = emptyMap()
    ) {
        /** Whether this is the project the lockfile belongs to, which uv records as an editable or virtual package. */
        val isRoot: Boolean
            get() = source["editable"] == "." || source["virtual"] == "."
    }

    /** A dependency edge, which may request extras of the package it points to. */
    @Serializable
    data class Dependency(
        val name: String,
        val extra: List<String> = emptyList()
    )
}

/**
 * The parts of a `pdm.lock` file that are needed to determine the packages a PEP 751 export of it must contain. PDM
 * records the dependency groups of each package, with "default" being the implicit group of runtime dependencies.
 */
@Serializable
private data class PdmLockfile(
    @SerialName("package")
    val packages: List<Package> = emptyList()
) {
    @Serializable
    data class Package(
        val name: String,
        val version: String? = null,
        val groups: List<String> = emptyList()
    )
}

/**
 * The parts of a `poetry.lock` file that are needed to determine the packages a PEP 751 export of it must contain.
 * Poetry records the dependency groups of each package, or the category "main" or "dev" before Poetry 1.2. Packages
 * that are only needed for an extra are marked as optional.
 */
@Serializable
private data class PoetryLockfile(
    @SerialName("package")
    val packages: List<Package> = emptyList()
) {
    @Serializable
    data class Package(
        val name: String,
        val version: String? = null,
        val groups: List<String>? = null,
        val category: String? = null,
        val optional: Boolean = false
    ) {
        val effectiveGroups: List<String>
            get() = groups ?: listOfNotNull(category).ifEmpty { listOf(POETRY_MAIN_GROUP) }
    }
}

/**
 * A package of a tool's lockfile, reduced to what is needed to compare it with a PEP 751 lockfile.
 */
private data class LockedPackage(val name: String, val version: String?, val groups: List<String>)

/**
 * The lockfile of the tool a PEP 751 lockfile was exported from. As developers maintain the tool's lockfile, a PEP
 * 751 export that does not match it is outdated.
 */
internal class NativeLockfile(
    /**
     * The tool's lockfile.
     */
    val file: File,

    /** The locked versions per normalized package name. */
    val versions: Map<String, Set<String>>,

    /**
     * The normalized names of the packages the PEP 751 lockfile must contain, i.e. those of the dependency groups and
     * extras it claims to cover. Packages of other groups may be missing on purpose.
     */
    val requiredNames: Set<String>
) {
    /**
     * Return a message for each package that differs between this lockfile and the PEP 751 lockfile with the given
     * [pylockVersions]. A package is reported if it is required but missing, unknown to this lockfile, or locked in
     * different versions. Local source trees have no version in a PEP 751 lockfile and are compared by name only.
     */
    fun findDivergences(pylockFile: File, pylockVersions: Map<String, Set<String>>): List<String> =
        (requiredNames + pylockVersions.keys).sorted().mapNotNull { name ->
            val locked = pylockVersions[name]
            val native = versions[name]

            when {
                locked == null -> "'$name' is in '${file.name}' but not in '${pylockFile.name}'."

                "" in locked -> null

                native == null -> "'$name' is in '${pylockFile.name}' but not in '${file.name}'."

                native.map(String::normalizePythonVersion).none { it in locked.map(String::normalizePythonVersion) } ->
                    "'$name' has version ${native.joinToString()} in '${file.name}' but version " +
                        "${locked.joinToString()} in '${pylockFile.name}'."

                else -> null
            }
        }
}

/**
 * Find the lockfiles of uv, PDM and Poetry in the [directory] of the given [pylock] file. A lockfile that cannot be
 * parsed is skipped with a warning.
 */
internal fun findNativeLockfiles(directory: File, pylock: PylockFile): List<NativeLockfile> {
    val groups = (pylock.dependencyGroups + pylock.defaultGroups).toSet()

    return listOfNotNull(
        directory.resolve(UV_LOCKFILE_NAME).takeIf { it.isFile }?.let { file ->
            parseNativeLockfile<UvLockfile>(file)?.toNativeLockfile(file, pylock.extras.toSet(), groups)
        },
        directory.resolve(PDM_LOCKFILE_NAME).takeIf { it.isFile }?.let { file ->
            parseNativeLockfile<PdmLockfile>(file)?.toNativeLockfile(file, groups)
        },
        directory.resolve(POETRY_LOCKFILE_NAME).takeIf { it.isFile }?.let { file ->
            parseNativeLockfile<PoetryLockfile>(file)?.toNativeLockfile(file, groups)
        }
    )
}

private inline fun <reified T> parseNativeLockfile(file: File): T? =
    runCatching {
        file.reader().use { toml.decodeFromNativeReader<T>(it) }
    }.onFailure {
        logger.warn { "Unable to parse '$file': ${it.message}" }
    }.getOrNull()

/**
 * Convert this uv lockfile by collecting the packages reachable from the project's dependencies plus the given
 * [extras] and dependency [groups]. Extras requested by a dependency edge activate the optional dependencies of the
 * package it points to.
 */
private fun UvLockfile.toNativeLockfile(file: File, extras: Set<String>, groups: Set<String>): NativeLockfile {
    val packagesByName = packages.groupBy { it.name.normalizePythonPackageName() }

    val queue = ArrayDeque<UvLockfile.Dependency>()

    packages.filter { it.isRoot }.forEach { root ->
        queue += root.dependencies
        extras.forEach { extra -> queue += root.optionalDependencies[extra].orEmpty() }
        groups.forEach { group -> queue += root.devDependencies[group].orEmpty() }
    }

    // A package is visited once for its own dependencies and once per extra requested for it.
    val visited = mutableSetOf<Pair<String, String?>>()

    while (queue.isNotEmpty()) {
        val dependency = queue.removeFirst()
        val name = dependency.name.normalizePythonPackageName()
        val dependencyPackages = packagesByName[name].orEmpty()

        if (visited.add(name to null)) dependencyPackages.flatMapTo(queue) { it.dependencies }

        dependency.extra.forEach { extra ->
            if (visited.add(name to extra)) {
                dependencyPackages.flatMapTo(queue) { it.optionalDependencies[extra].orEmpty() }
            }
        }
    }

    return NativeLockfile(
        file = file,
        versions = packagesByName.mapValues { (_, packages) -> packages.mapNotNullTo(mutableSetOf()) { it.version } },
        requiredNames = visited.mapTo(mutableSetOf()) { (name, _) -> name }
    )
}

/**
 * Convert this PDM lockfile by selecting the packages of the given dependency [groups]. PDM only records the groups
 * of an export if all groups were exported, so no package is required if the export names no groups.
 */
private fun PdmLockfile.toNativeLockfile(file: File, groups: Set<String>): NativeLockfile =
    packages.map { LockedPackage(it.name, it.version, it.groups) }.toNativeLockfile(file, groups)

/**
 * Convert this Poetry lockfile by selecting the packages of the implicit main group plus the given dependency
 * [groups], like `poetry export` does. Optional packages are never required, as they are only exported for
 * explicitly requested extras.
 */
private fun PoetryLockfile.toNativeLockfile(file: File, groups: Set<String>): NativeLockfile =
    packages.map { pkg ->
        LockedPackage(pkg.name, pkg.version, pkg.effectiveGroups.takeUnless { pkg.optional }.orEmpty())
    }.toNativeLockfile(file, groups + POETRY_MAIN_GROUP)

/**
 * Convert these packages to a lockfile that requires the packages of the given [groups].
 */
private fun List<LockedPackage>.toNativeLockfile(file: File, groups: Set<String>): NativeLockfile =
    NativeLockfile(
        file = file,
        versions = groupBy({ it.name.normalizePythonPackageName() }, { it.version })
            .mapValues { (_, versions) -> versions.filterNotNullTo(mutableSetOf()) },
        requiredNames = filter { pkg -> pkg.groups.any { it in groups } }
            .mapTo(mutableSetOf()) { it.name.normalizePythonPackageName() }
    )
