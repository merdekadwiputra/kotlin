/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.report

import com.intellij.util.text.DateFormatUtil.formatTime
import org.gradle.api.invocation.Gradle
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskOperationDescriptor
import java.io.File

class KotlinBuildReporterListener(val gradle: Gradle, val perfReportFile: File) : OperationCompletionListener {

    override fun onFinish(event: FinishEvent?) {
        val logger = gradle.rootProject.logger
        try {
            event?.let { perfReportFile.appendText(taskOverview(it)) }
            logger.lifecycle("Kotlin build report is written to ${perfReportFile.canonicalPath}")
        } catch (e: Throwable) {
            logger.error("Could not write Kotlin build report to ${perfReportFile.canonicalPath}", e)
        }
        KotlinBuildReporterHandler().buildFinished(gradle, perfReportFile, null)
    }

    internal fun taskOverview(event: FinishEvent): String {
        val sb = StringBuilder()
        val descriptor = event.descriptor
        if (descriptor is TaskOperationDescriptor) {
            val taskPath = descriptor.taskPath
            val executionTime = event.result.endTime - event.result.startTime
            //TODO check it
            KotlinBuildReporterHandler.allTasksTimeNs += executionTime
            val delimeter = "|"
            sb.append(formatTime(executionTime)).append(delimeter).append(taskPath)
        }

        return sb.toString()
    }
}