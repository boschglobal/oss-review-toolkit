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

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.common.div

class PythonRequirementsTest : WordSpec({
    "parseRequirementString()" should {
        "parse a plain name" {
            parseRequirementString("requests") shouldBe PythonRequirement("requests")
        }

        "normalize the name" {
            parseRequirementString("Typed_Ast >= 1.5") shouldBe PythonRequirement("typed-ast")
        }

        "extract an exact version pin" {
            parseRequirementString("cattrs==24.1.2") shouldBe PythonRequirement("cattrs", pinnedVersion = "24.1.2")
            parseRequirementString("cattrs == 24.1.2") shouldBe PythonRequirement("cattrs", pinnedVersion = "24.1.2")
            parseRequirementString("cattrs===24.1.2") shouldBe
                PythonRequirement("cattrs", pinnedVersion = "24.1.2", isArbitraryPin = true)
            parseRequirementString("cattrs (==24.1.2)") shouldBe PythonRequirement("cattrs", pinnedVersion = "24.1.2")
            parseRequirementString("cattrs>=24,==24.1.2") shouldBe PythonRequirement("cattrs", pinnedVersion = "24.1.2")
        }

        "not treat other specifiers as a pin" {
            parseRequirementString("cattrs>=24.1.2") shouldBe PythonRequirement("cattrs")
            parseRequirementString("cattrs~=24.1.2") shouldBe PythonRequirement("cattrs")
            parseRequirementString("cattrs!=24.1.2") shouldBe PythonRequirement("cattrs")
            parseRequirementString("cattrs==24.1.*") shouldBe PythonRequirement("cattrs")
        }

        "handle extras and markers" {
            parseRequirementString("requests[security,socks]==2.32.0; python_version < '3.12'") shouldBe
                PythonRequirement("requests", pinnedVersion = "2.32.0", hasMarker = true)
            parseRequirementString("colorama; sys_platform == 'win32'") shouldBe
                PythonRequirement("colorama", hasMarker = true)
        }

        "not take a pin from a direct reference URL" {
            parseRequirementString("pip @ https://github.com/pypa/pip/archive/1.3.1.zip?ref==foo") shouldBe
                PythonRequirement("pip")
        }

        "return null for anything that is not a named requirement" {
            parseRequirementString("") should beNull()
            parseRequirementString("git+https://github.com/pypa/sampleproject.git#egg=sampleproject") should beNull()
            parseRequirementString("https://example.org/pkg.whl") should beNull()
            parseRequirementString("./local/path") should beNull()
            parseRequirementString("-e .") should beNull()
        }
    }

    "isSatisfiedBy()" should {
        "match versions by their normalized form" {
            PythonRequirement("cattrs", pinnedVersion = "24.1").isSatisfiedBy("24.1.0") shouldBe true
            PythonRequirement("cattrs", pinnedVersion = "24.1").isSatisfiedBy("24.1.1") shouldBe false
        }

        "ignore the local version label of the locked version if the pin has none" {
            PythonRequirement("torch", pinnedVersion = "2.6.0").isSatisfiedBy("2.6.0+cpu") shouldBe true
            PythonRequirement("torch", pinnedVersion = "2.6.0+cpu").isSatisfiedBy("2.6.0+cpu") shouldBe true
            PythonRequirement("torch", pinnedVersion = "2.6.0+cpu").isSatisfiedBy("2.6.0+cu124") shouldBe false
            PythonRequirement("torch", pinnedVersion = "2.6.0+cpu").isSatisfiedBy("2.6.0") shouldBe false
        }

        "compare an arbitrary equality pin literally" {
            val pin = PythonRequirement("cattrs", pinnedVersion = "24.1.0", isArbitraryPin = true)

            pin.isSatisfiedBy("24.1.0") shouldBe true
            pin.isSatisfiedBy("24.1") shouldBe false
        }

        "be satisfied by anything without a pin" {
            PythonRequirement("cattrs").isSatisfiedBy("24.1.0") shouldBe true
        }
    }

    "parseRequirementsFile()" should {
        "parse requirements and follow includes while skipping comments and options" {
            val dir = tempdir()

            (dir / "requirements.txt").writeText(
                """
                    # A comment line.
                    --index-url https://pypi.org/simple
                    cattrs == 24.1.2  # A trailing comment.
                    colorama==0.4.6 ; sys_platform == 'win32'
                    requests>=2.32 \
                        --hash=sha256:0000000000000000000000000000000000000000000000000000000000000000
                    -r requirements-dev.txt
                    --requirement=requirements-dev.txt
                    -e .
                    ./vendored/package
                    git+https://github.com/pypa/sampleproject.git#egg=sampleproject
                """.trimIndent()
            )

            (dir / "requirements-dev.txt").writeText(
                """
                    pytest==8.3.5
                    -r requirements.txt
                """.trimIndent()
            )

            parseRequirementsFile(dir / "requirements.txt") should containExactly(
                PythonRequirement("cattrs", pinnedVersion = "24.1.2"),
                PythonRequirement("colorama", pinnedVersion = "0.4.6", hasMarker = true),
                PythonRequirement("requests"),
                PythonRequirement("pytest", pinnedVersion = "8.3.5")
            )
        }

        "return no requirements for a non-existing file" {
            parseRequirementsFile(tempdir() / "requirements.txt") should beEmpty()
        }
    }

    "normalizeVersionSpecifier()" should {
        "ignore whitespace, the order of clauses and trailing zero segments" {
            ">= 3.12.0, <4.0".normalizeVersionSpecifier() shouldBe setOf(">=3.12", "<4")
            "<4,>=3.12".normalizeVersionSpecifier() shouldBe setOf(">=3.12", "<4")
        }

        "keep the version of a compatible release clause as is" {
            "~= 3.12.0".normalizeVersionSpecifier() shouldBe setOf("~=3.12.0")
        }

        "keep an unparsable clause as is" {
            "~3.12".normalizeVersionSpecifier() shouldBe setOf("~3.12")
        }
    }

    "normalizePythonVersion()" should {
        "drop trailing zero release segments, the case and a leading 'v'" {
            "1.0".normalizePythonVersion() shouldBe "1"
            "1.0.0".normalizePythonVersion() shouldBe "1"
            "v1.2.0".normalizePythonVersion() shouldBe "1.2"
            "1.0.0RC1".normalizePythonVersion() shouldBe "1rc1"
            "2!1.0.post1".normalizePythonVersion() shouldBe "2!1.post1"
            "0.0".normalizePythonVersion() shouldBe "0"
        }

        "keep an unparsable version as is" {
            "latest".normalizePythonVersion() shouldBe "latest"
        }
    }
})
