/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.ossreviewtoolkit.clients.fossid.model.summary.Summarizable
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.SnippetChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

private val SNIPPET_CHOICE = SnippetChoice(
    TextLocation("test.kt", 1),
    "This is a snippet choice",
    "MIT".toSpdx(),
    "pkg:github/zPeanut/Hydrogen@1.8.0-beta"
)

private val SNIPPET_FINDING = SnippetFinding(
    TextLocation("test.kt", 1),
    setOf(
        Snippet(
            1F,
            TextLocation("src/main/java/com/vdurmont/semver4j/Range.java", 1),
            ArtifactProvenance(RemoteArtifact("https://github.com/nnobelis/Semver4j.git", Hash.NONE)),
            "pkg:github/zPeanut/Hydrogen@1.8.0-beta",
            "MIT".toSpdx()
        )
    )
)

class FossIdSnippetChoiceTest : WordSpec({
    "mapSummaryShould" should {
        "create an issue when the file referenced by the snippet choice has no snippet" {
            val issues = mutableListOf<Issue>()
            val snippetFindings = setOf(SNIPPET_FINDING)
            val snippetChoices =
                listOf(SNIPPET_CHOICE.copy(sourceLocation = SNIPPET_CHOICE.sourceLocation.copy(path = "missing.kt")))

            val findings = emptyList<Summarizable>().mapSummary(emptyMap(), issues, emptyMap(), snippetFindings, snippetChoices)

            findings.licenseFindings should beEmpty()
            issues shouldHaveSize 1
            issues.first() shouldNotBeNull {
                message shouldBe "Snippet choice's file missing.kt is not in the snippet results"
            }
        }

        "create an issue when the snippet referenced by the snippet choice does not exist" {
            val issues = mutableListOf<Issue>()
            val snippetFindings = setOf(SNIPPET_FINDING)
            val snippetChoices = listOf(SNIPPET_CHOICE.copy(snippet = "AAA"))

            val findings = emptyList<Summarizable>().mapSummary(emptyMap(), issues, emptyMap(), snippetFindings, snippetChoices)

            findings.licenseFindings should beEmpty()
            issues shouldHaveSize 1
            issues.first() shouldNotBeNull {
                message shouldBe "Snippet choice's snippet AAA is not in the snippet results"
            }
        }

        "create an issue when the snippet choice contains a different license as the snippet, but create a license " +
                "finding nevertheless" {
            val issues = mutableListOf<Issue>()
            val snippetFindings = setOf(SNIPPET_FINDING)
            val snippetChoices = listOf(SNIPPET_CHOICE.copy(license = "Apache-2.0".toSpdx()))

            val findings = emptyList<Summarizable>().mapSummary(emptyMap(), issues, emptyMap(), snippetFindings, snippetChoices)

            findings.licenseFindings shouldHaveSize 1
            findings.licenseFindings.first() shouldNotBeNull {
                license shouldBe "MIT".toSpdx()
                location shouldBe SNIPPET_FINDING.sourceLocation
            }
            issues shouldHaveSize 1
            issues.first() shouldNotBeNull {
                message shouldBe "Snippet choice's license Apache-2.0 is different from the actual snippet license " +
                        "MIT. Snippet choice cannot be used to change snippet license."
                severity shouldBe Severity.ERROR
            }
        }

        "create a license finding when the snippet choice is correct" {
            val issues = mutableListOf<Issue>()
            val snippetFindings = setOf(SNIPPET_FINDING)
            val snippetChoices = listOf(SNIPPET_CHOICE)

            val findings = emptyList<Summarizable>().mapSummary(emptyMap(), issues, emptyMap(), snippetFindings, snippetChoices)

            findings.licenseFindings shouldHaveSize 1
            findings.licenseFindings.first() shouldNotBeNull {
                license shouldBe "MIT".toSpdx()
                location shouldBe SNIPPET_FINDING.sourceLocation
            }
            issues should beEmpty()
        }
    }
})
