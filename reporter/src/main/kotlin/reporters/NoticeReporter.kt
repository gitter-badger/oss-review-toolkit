/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.reporter.Reporter
import com.here.ort.utils.spdx.getLicenseText

import java.io.File
import java.util.SortedSet

class NoticeReporter : Reporter {
    override fun generateReport(ortResult: OrtResult, outputDir: File) {
        require(ortResult.scanner != null) {
            "The provided ORT result file does not contain a scan record."
        }

        val scanRecord = ortResult.scanner!!.results

        val noticeFile = File(outputDir, "NOTICE")

        val allFindings = sortedMapOf<String, SortedSet<String>>()

        // TODO: Decide whether we want to merge the list of detected licenses with declared licenses (which do not come
        // with a copyright).
        scanRecord.scanResults.forEach { container ->
            if (ortResult.analyzer?.result?.includesPackage(container.id) == true) {
                container.results.forEach { result ->
                    result.summary.licenseFindings.forEach { licenseFinding ->
                        allFindings.getOrPut(licenseFinding.license) { sortedSetOf() } += licenseFinding.copyrights
                    }
                }
            }
        }

        val findingsIterator = allFindings.filterNot { (license, _) ->
            // For now, just skip license references for which SPDX has no license text.
            license.startsWith("LicenseRef-")
        }.iterator()

        // Note: Do not use appendln() here as that would write out platform-native line endings, but we want to
        // normalize on Unix-style line endings for consistency.
        while (findingsIterator.hasNext()) {
            val (license, copyrights) = findingsIterator.next()

            var noticeBuilder = StringBuilder()

            copyrights.forEach { copyright ->
                noticeBuilder.append("$copyright\n")
            }

            if (copyrights.isNotEmpty()) noticeBuilder.append("\n")

            noticeBuilder.append("${getLicenseText(license)}\n")

            // Trim lines and remove consecutive blank lines as the license text formatting in SPDX JSON files is
            // broken, see https://github.com/spdx/LicenseListPublisher/issues/30.
            var previousLine = ""
            val trimmedNoticeLines = noticeBuilder.lines().mapNotNull { line ->
                val trimmedLine = line.trim()
                trimmedLine.takeIf { it.isNotBlank() || previousLine.isNotBlank() }
                        .also { previousLine = trimmedLine }
            }

            noticeBuilder = StringBuilder(trimmedNoticeLines.joinToString("\n"))

            // Separate notice entries.
            if (findingsIterator.hasNext()) noticeBuilder.append("\n----\n\n")

            noticeFile.appendText(noticeBuilder.toString())
        }
    }
}
