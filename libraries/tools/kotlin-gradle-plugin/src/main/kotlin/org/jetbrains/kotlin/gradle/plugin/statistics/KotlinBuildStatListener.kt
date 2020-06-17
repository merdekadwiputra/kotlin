/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.statistics

import org.gradle.api.invocation.Gradle
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.jetbrains.kotlin.statistics.BuildSessionLogger
import org.jetbrains.kotlin.statistics.metrics.NumericalMetrics
import java.lang.management.ManagementFactory
import javax.management.MBeanServer
import javax.management.ObjectName

open class KotlinBuildStatListener(val beanName: ObjectName) : OperationCompletionListener {

    override fun onFinish(event: FinishEvent?) {
        //todo is it any chance to get failure exception?
        KotlinBuildStatHandler.runSafe("${KotlinBuildStatHandler::class.java}.buildFinished") {
            //TODO store metrics
//            try {
//                val endTime = event?.result?.endTime
//                try {
//                    if (gradle != null) reportGlobalMetrics(gradle, sessionLogger)
//                } finally {
//                    report(NumericalMetrics.GRADLE_BUILD_DURATION, finishTime - it.buildStartedTime)
//                    report(NumericalMetrics.GRADLE_EXECUTION_DURATION, finishTime - it.projectEvaluatedTime)
//                    report(NumericalMetrics.BUILD_FINISH_TIME, finishTime)
//                }
//            } finally {
//                val mbs: MBeanServer = ManagementFactory.getPlatformMBeanServer()
//                if (mbs.isRegistered(beanName)) {
//                    mbs.unregisterMBean(beanName)
//                }
//            }
        }

    }
}