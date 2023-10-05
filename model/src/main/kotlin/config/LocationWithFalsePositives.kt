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

package org.ossreviewtoolkit.model.config

import org.ossreviewtoolkit.model.TextLocation

/**
 * When a [SnippetChoice] is made, all the other snippet are automatically false positives. When a file has only false
 * positives, [LocationWithFalsePositives] can be used to mark all the matching snippets as false positives.
 */
data class LocationWithFalsePositives(
    /**
     * The source file location having only false positives snippets.
     */
    val sourceLocation: TextLocation,

    /**
     * The reason why all the matching snippets are a false positive.
     */
    val reasoning: String
)
