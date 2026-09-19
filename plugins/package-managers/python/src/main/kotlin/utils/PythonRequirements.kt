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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import java.io.File
import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.plugins.packagemanagers.python.normalizePythonPackageName

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * A dependency specifier, see https://packaging.python.org/en/latest/specifications/dependency-specifiers/, reduced
 * to the name with optional extras, followed by a version specifier, a URL reference, a marker or nothing. Plain URLs
 * and local paths are not named requirements.
 */
private val REQUIREMENT_REGEX = Regex(
    """^([A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?)\s*(?:\[[^]]*])?\s*(?:$|([<>=!~@;(].*)$)"""
)

/**
 * An exact version clause. The arbitrary equality operator `===` is included as it also pins a single version, and
 * the operator is captured as the versions are compared differently.
 */
private val EXACT_VERSION_REGEX = Regex("""(===?)\s*([^,;\s)]+)""")

/** A single clause of a version specifier, consisting of an operator and a version. */
private val VERSION_CLAUSE_REGEX = Regex("""^(===?|~=|!=|<=|>=|<|>)\s*(\S+)$""")

/** A `-r` / `--requirement` option that includes another requirements file. */
private val REQUIREMENTS_INCLUDE_REGEX = Regex("""^(?:-r|--requirement)[ =](.+)$""")

/** A comment in a requirements file, which starts with a `#` at the beginning of the line or after whitespace. */
private val REQUIREMENTS_COMMENT_REGEX = Regex("""(^|\s)#.*$""")

/**
 * A requirement from a `requirements.txt` or `pyproject.toml` file, reduced to what is needed to compare it with a
 * lockfile.
 */
internal data class PythonRequirement(
    /** The normalized name of the required package. */
    val name: String,

    /**
     * The version the requirement is pinned to with an exact version clause, if any. Wildcards like `==1.0.*` do not
     * pin a single version and are ignored.
     */
    val pinnedVersion: String? = null,

    /** Whether the requirement is conditional on an environment marker. */
    val hasMarker: Boolean = false,

    /** Whether the version is pinned with the arbitrary equality operator `===`, which compares versions literally. */
    val isArbitraryPin: Boolean = false
) {
    /**
     * Return whether the given [lockedVersion] satisfies the pinned version, if any. A pin without a local version
     * label matches any local version, e.g. `==2.6.0` is satisfied by `2.6.0+cpu`.
     */
    fun isSatisfiedBy(lockedVersion: String): Boolean {
        val pinnedVersion = pinnedVersion ?: return true

        if (isArbitraryPin) return pinnedVersion == lockedVersion

        val comparableVersion = if ('+' in pinnedVersion) lockedVersion else lockedVersion.substringBefore('+')

        return pinnedVersion.normalizePythonVersion() == comparableVersion.normalizePythonVersion()
    }
}

/**
 * Parse the given [requirement] string, or return null if it is not a named requirement.
 */
internal fun parseRequirementString(requirement: String): PythonRequirement? {
    val match = REQUIREMENT_REGEX.matchEntire(requirement.trim()) ?: return null

    val name = match.groupValues[1].normalizePythonPackageName()
    val (specifiers, marker) = match.groupValues[2].split(';', limit = 2).let { it.first() to it.getOrNull(1) }

    // A direct reference like "name @ https://..." has no version specifiers, so the "==" of a URL query parameter
    // must not be taken as a version pin.
    val pin = specifiers.trim().takeUnless { it.startsWith('@') }?.let {
        EXACT_VERSION_REGEX.find(it)?.takeUnless { match -> match.groupValues[2].endsWith(".*") }
    }

    return PythonRequirement(
        name,
        pinnedVersion = pin?.groupValues?.get(2),
        hasMarker = !marker.isNullOrBlank(),
        isArbitraryPin = pin?.groupValues?.get(1) == "==="
    )
}

/**
 * Parse the requirements in the given [file], following includes of other requirements files. Options and editable
 * installs are skipped. The [visited] set prevents endless recursion for files that include each other.
 */
internal fun parseRequirementsFile(file: File, visited: MutableSet<File> = mutableSetOf()): List<PythonRequirement> {
    if (!visited.add(file.canonicalFile)) return emptyList()

    if (!file.isFile) {
        logger.warn { "The requirements file '$file' does not exist." }
        return emptyList()
    }

    // A backslash at the end of a line continues the logical line.
    val logicalLines = file.readText().replace("\\\r\n", "").replace("\\\n", "").lines()

    return logicalLines.flatMap { logicalLine ->
        val line = REQUIREMENTS_COMMENT_REGEX.replace(logicalLine, "").trim()

        REQUIREMENTS_INCLUDE_REGEX.matchEntire(line)?.let { match ->
            return@flatMap parseRequirementsFile(file.resolveSibling(match.groupValues[1].trim()), visited)
        }

        when {
            line.isEmpty() || line.startsWith('-') -> emptyList()
            else -> listOfNotNull(parseRequirementString(line))
        }
    }
}

/**
 * Normalize this version specifier, like `>= 3.12.0, <4.0`, to a set of clauses without whitespace and with
 * normalized versions, so that two specifiers can be compared regardless of their notation. The versions of `~=` and
 * `===` clauses are kept as they are, as their trailing zeros are significant.
 */
internal fun String.normalizeVersionSpecifier(): Set<String> =
    split(',').mapTo(mutableSetOf()) { clause ->
        val trimmedClause = clause.trim()

        VERSION_CLAUSE_REGEX.matchEntire(trimmedClause)?.destructured?.let { (operator, version) ->
            if (operator in setOf("~=", "===")) "$operator$version" else "$operator${version.normalizePythonVersion()}"
        } ?: trimmedClause
    }

/**
 * Normalize this version string for comparison. Only the case, a leading `v` and trailing zero segments are
 * normalized, as that is what typically differs between a pinned version and the version in a lockfile.
 */
internal fun String.normalizePythonVersion(): String {
    val version = trim().lowercase().removePrefix("v")
    val match = Regex("""^(\d+!)?(\d+(?:\.\d+)*)(.*)$""").matchEntire(version) ?: return version

    val (epoch, release, rest) = match.destructured
    val normalizedRelease = release.split('.').dropLastWhile { it == "0" }.ifEmpty { listOf("0") }.joinToString(".")

    return "$epoch$normalizedRelease$rest"
}
