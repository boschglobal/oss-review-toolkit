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

package org.ossreviewtoolkit.model

import org.ossreviewtoolkit.model.utils.prependPath

/**
 * A [TextLocation] references text located in a file at multiple positions.
 */
data class TextWithMultipleLocations(
    /**
     * The path (with invariant separators) of the file that contains the text.
     */
    val path: String,

    /**
     * A collection of ranges where the text is occurring. Each range is the starting line and the ending line.
     */
    val lines: Set<LineRange>
) : Comparable<TextWithMultipleLocations> {
    companion object {
        const val UNKNOWN_LINE = -1
    }

    init {
        require(path.isNotEmpty()) {
            "The path must not be empty."
        }
    }

    /**
     * A convenience constructor that constructs a [TextWithMultipleLocations] from a [TextLocation].
     */
    constructor(location: TextLocation) : this(location.path, LineRange(location.startLine, location.endLine))

    /**
     * A convenience constructor that accepts multiple ranges as vararg.
     */
    constructor(path: String, vararg lineRanges: LineRange) : this(path, lineRanges.toSet())

    /**
     * The [Comparable] implementation for a [TextWithMultipleLocations]: the paths are used for a quick comparison.
     * If the paths are identical, the [lines] are then compared for equality.
     */
    override fun compareTo(other: TextWithMultipleLocations): Int {
        val pathComparison = path.compareTo(other.path)
        return if (pathComparison == 0) {
            if (lines == other.lines) 0 else -1
        } else {
            pathComparison
        }
    }

    fun prependPath(prefix: String): TextWithMultipleLocations =
        if (prefix.isEmpty()) this else copy(path = path.prependPath(prefix))
}

/**
 * A range is a single location in a [TextWithMultipleLocations].
 */
data class LineRange(
    /**
     * The line the text is starting at.
     */
    val startLine: Int,

    /**
     * The line the text is ending at.
     */
    val endLine: Int
) : Comparable<LineRange> {
    companion object {
        private val COMPARATOR = compareBy<LineRange> { it.startLine }.thenBy { it.endLine }
    }

    init {
        require(
            startLine in 1..endLine ||
                    (startLine == TextLocation.UNKNOWN_LINE && endLine == TextLocation.UNKNOWN_LINE)
        ) {
            "Invalid start or end line values."
        }
    }

    /**
     * A convenience constructor that sets [startLine] and [endLine] to the same [line].
     */
    constructor(line: Int) : this(line, line)

    override fun compareTo(other: LineRange) = COMPARATOR.compare(this, other)
}
