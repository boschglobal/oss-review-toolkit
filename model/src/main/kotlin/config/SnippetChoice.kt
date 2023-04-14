/*
 * Copyright (C) 2023 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A snippet choice for a given source file.
 */
data class SnippetChoice(
    /**
     * The source file for which the snippet choice is made.
     */
    val sourceLocation: TextLocation,
    /**
     * The reason why this snippet choice is made
     */
    val reasoning: String,
    /**
     * The license of the snippet chosen by this snippet choice.
     */
    val license: SpdxExpression,
    /**
     * The purl of the snippet chosen by this snippet choice
     */
    val snippet: String
)
