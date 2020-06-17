/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast

class BuildEventsListenerRegistryHolder(val listenerRegistry: BuildEventsListenerRegistry?) {
    companion object {
        fun getInstance(project: Project) = run {
            if (isGradleVersionAtLeast(6, 1)) {
                BuildEventsListenerRegistryHolder(project.objects.newInstance(BuildEventsListenerRegistry::class.java))
            } else {
                BuildEventsListenerRegistryHolder(null)
            }
        }
    }
}