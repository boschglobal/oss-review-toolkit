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

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.common.div

private val UV_LOCK = """
    version = 1
    revision = 3
    requires-python = ">=3.12"

    [[package]]
    name = "attrs"
    version = "26.1.0"
    source = { registry = "https://pypi.org/simple" }

    [package.optional-dependencies]
    tests = [
        { name = "hypothesis" },
    ]

    [[package]]
    name = "cattrs"
    version = "24.1.2"
    source = { registry = "https://pypi.org/simple" }
    dependencies = [
        { name = "attrs" },
    ]

    [[package]]
    name = "hypothesis"
    version = "6.130.0"
    source = { registry = "https://pypi.org/simple" }

    [[package]]
    name = "pluggy"
    version = "1.6.0"
    source = { registry = "https://pypi.org/simple" }

    [[package]]
    name = "pylock-sample"
    version = "0.1.0"
    source = { editable = "." }
    dependencies = [
        { name = "cattrs" },
        { name = "workspace-member" },
    ]

    [package.optional-dependencies]
    yaml = [
        { name = "pyyaml" },
    ]

    [package.dev-dependencies]
    dev = [
        { name = "pytest" },
    ]

    [[package]]
    name = "pytest"
    version = "8.3.5"
    source = { registry = "https://pypi.org/simple" }
    dependencies = [
        { name = "attrs", extra = ["tests"] },
        { name = "pluggy" },
    ]

    [[package]]
    name = "pyyaml"
    version = "6.0.2"
    source = { registry = "https://pypi.org/simple" }

    [[package]]
    name = "workspace-member"
    version = "0.1.0"
    source = { editable = "packages/member" }
""".trimIndent()

private val PDM_LOCK = """
    [metadata]
    groups = ["default", "dev"]
    lock_version = "4.5.0"

    [[package]]
    name = "cattrs"
    version = "24.1.2"
    groups = ["default"]

    [[package]]
    name = "pluggy"
    version = "1.6.0"
    groups = ["default", "dev"]

    [[package]]
    name = "pytest"
    version = "8.3.5"
    groups = ["dev"]
""".trimIndent()

private val POETRY_LOCK = """
    [[package]]
    name = "cattrs"
    version = "24.1.2"
    groups = ["main"]

    [[package]]
    name = "pytest"
    version = "8.3.5"
    groups = ["dev"]

    [[package]]
    name = "legacy-dev-tool"
    version = "1.0"
    category = "dev"

    [[package]]
    name = "legacy-runtime"
    version = "1.0"

    [[package]]
    name = "ipywidgets"
    version = "8.1.5"
    optional = true
    groups = ["main"]
    markers = "extra == \"jupyter\""
""".trimIndent()

private fun pylock(
    dependencyGroups: List<String> = emptyList(),
    extras: List<String> = emptyList(),
    defaultGroups: List<String> = emptyList()
) = PylockFile(
    lockVersion = "1.0",
    createdBy = "test",
    dependencyGroups = dependencyGroups,
    defaultGroups = defaultGroups,
    extras = extras
)

class NativeLockfileTest : WordSpec({
    fun File.withLockfile(name: String, content: String): File = also { (it / name).writeText(content) }

    "findNativeLockfiles()" should {
        "collect the packages reachable from the project's runtime dependencies for uv" {
            val dir = tempdir().withLockfile("uv.lock", UV_LOCK)

            val lockfile = findNativeLockfiles(dir, pylock()).single()

            lockfile.file.name shouldBe "uv.lock"
            lockfile.requiredNames should containExactlyInAnyOrder("attrs", "cattrs", "workspace-member")
            lockfile.versions shouldBe mapOf(
                "attrs" to setOf("26.1.0"),
                "cattrs" to setOf("24.1.2"),
                "hypothesis" to setOf("6.130.0"),
                "pluggy" to setOf("1.6.0"),
                "pylock-sample" to setOf("0.1.0"),
                "pytest" to setOf("8.3.5"),
                "pyyaml" to setOf("6.0.2"),
                "workspace-member" to setOf("0.1.0")
            )
        }

        "include the extras and dependency groups the PEP 751 lockfile covers for uv" {
            val dir = tempdir().withLockfile("uv.lock", UV_LOCK)

            val lockfile = findNativeLockfiles(dir, pylock(dependencyGroups = listOf("dev"), extras = listOf("yaml")))
                .single()

            // The development group depends on an extra of a runtime package, so its optional dependency is
            // required, too.
            lockfile.requiredNames should containExactlyInAnyOrder(
                "attrs", "cattrs", "hypothesis", "pluggy", "pytest", "pyyaml", "workspace-member"
            )
        }

        "select the packages of the covered groups for PDM" {
            val dir = tempdir().withLockfile("pdm.lock", PDM_LOCK)

            val lockfile = findNativeLockfiles(dir, pylock(defaultGroups = listOf("default"))).single()

            lockfile.requiredNames should containExactlyInAnyOrder("cattrs", "pluggy")
            lockfile.versions.keys should containExactlyInAnyOrder("cattrs", "pluggy", "pytest")
        }

        "require no packages for PDM if the PEP 751 lockfile names no groups" {
            val dir = tempdir().withLockfile("pdm.lock", PDM_LOCK)

            val lockfile = findNativeLockfiles(dir, pylock()).single()

            lockfile.requiredNames should beEmpty()
        }

        "select the main group plus the covered groups for Poetry, honoring legacy categories" {
            val dir = tempdir().withLockfile("poetry.lock", POETRY_LOCK)

            // The optional package is only needed for an extra, so it is known but not required.
            findNativeLockfiles(dir, pylock()).single().apply {
                requiredNames should containExactlyInAnyOrder("cattrs", "legacy-runtime")
                versions.keys should containExactlyInAnyOrder(
                    "cattrs", "pytest", "legacy-dev-tool", "legacy-runtime", "ipywidgets"
                )
            }

            findNativeLockfiles(dir, pylock(dependencyGroups = listOf("dev"))).single().requiredNames should
                containExactlyInAnyOrder("cattrs", "legacy-runtime", "pytest", "legacy-dev-tool")
        }

        "find all lockfiles next to each other" {
            val dir = tempdir()
                .withLockfile("uv.lock", UV_LOCK)
                .withLockfile("pdm.lock", PDM_LOCK)
                .withLockfile("poetry.lock", POETRY_LOCK)

            findNativeLockfiles(dir, pylock()).map { it.file.name } should
                containExactly("uv.lock", "pdm.lock", "poetry.lock")
        }

        "skip a lockfile that cannot be parsed" {
            val dir = tempdir().withLockfile("uv.lock", "[[package]\nname = ")

            findNativeLockfiles(dir, pylock()) should beEmpty()
        }
    }

    "findDivergences()" should {
        val pylockFile = File("pylock.toml")

        "report packages that are missing or have another version" {
            val dir = tempdir().withLockfile("uv.lock", UV_LOCK)
            val lockfile = findNativeLockfiles(dir, pylock()).single()

            val pylockVersions = mapOf(
                "cattrs" to setOf("24.1.1"),
                "workspace-member" to setOf(""),
                "pylock-sample" to setOf(""),
                "requests" to setOf("2.32.0")
            )

            lockfile.findDivergences(pylockFile, pylockVersions) should containExactly(
                "'attrs' is in 'uv.lock' but not in 'pylock.toml'.",
                "'cattrs' has version 24.1.2 in 'uv.lock' but version 24.1.1 in 'pylock.toml'.",
                "'requests' is in 'pylock.toml' but not in 'uv.lock'."
            )
        }

        "report a version that differs even for a package that is not required" {
            val dir = tempdir().withLockfile("pdm.lock", PDM_LOCK)
            val lockfile = findNativeLockfiles(dir, pylock()).single()

            val pylockVersions = mapOf("cattrs" to setOf("24.1.2"), "pytest" to setOf("8.3.4"))

            lockfile.findDivergences(pylockFile, pylockVersions).shouldBeSingleton {
                it shouldBe "'pytest' has version 8.3.5 in 'pdm.lock' but version 8.3.4 in 'pylock.toml'."
            }
        }

        "not report packages that are locked in a matching version" {
            val dir = tempdir().withLockfile("pdm.lock", PDM_LOCK)
            val lockfile = findNativeLockfiles(dir, pylock()).single()

            val pylockVersions = mapOf(
                "cattrs" to setOf("24.1.2"),
                "pluggy" to setOf("1.6"),
                "pytest" to setOf("8.3.4", "8.3.5")
            )

            lockfile.findDivergences(pylockFile, pylockVersions) should beEmpty()
        }

        "not report packages of groups the PEP 751 lockfile does not cover" {
            val dir = tempdir().withLockfile("poetry.lock", POETRY_LOCK)
            val lockfile = findNativeLockfiles(dir, pylock()).single()

            val pylockVersions = mapOf("cattrs" to setOf("24.1.2"), "legacy-runtime" to setOf("1.0"))

            lockfile.findDivergences(pylockFile, pylockVersions) should beEmpty()
        }

        "report packages that only the PEP 751 lockfile contains" {
            val dir = tempdir().withLockfile("pdm.lock", PDM_LOCK)
            val lockfile = findNativeLockfiles(dir, pylock(defaultGroups = listOf("default"))).single()

            val pylockVersions = mapOf(
                "cattrs" to setOf("24.1.2"),
                "pluggy" to setOf("1.6.0"),
                "six" to setOf("1.17.0")
            )

            lockfile.findDivergences(pylockFile, pylockVersions).shouldBeSingleton {
                it shouldBe "'six' is in 'pylock.toml' but not in 'pdm.lock'."
            }
        }
    }
})
