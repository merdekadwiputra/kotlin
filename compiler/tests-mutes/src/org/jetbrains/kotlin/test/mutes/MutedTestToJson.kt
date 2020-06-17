/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.mutes

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val objectMapper = jacksonObjectMapper()

data class MuteTestJson(
    val id: Int,
    val assignment: JsonNode,
    val scope: JsonNode,
    val target: JsonNode,
    val resolution: JsonNode
)

internal fun MuteTestJson.getTests(): List<String> {
    return target.get("tests").get("test").toList().map { it.get("name").textValue() }
}

internal fun createMuteTestJson(testName: String, description: String, scope: Scope): MuteTestJson {
    val assignmentJson = """{ "text" : "$TAG $description" }"""
    val scopeJson = if (scope.isBuildType)
        """{"buildTypes":{"buildType":[{"id":"${scope.id}"}]}}"""
    else
        """{"project":{"id":"${scope.name}"}}"""
    val targetJson = """{ "tests" : { "test" : [ { "name" : "$testName" } ] } }"""
    val resolutionJson = """{ "type" : "manually" }"""

    return MuteTestJson(
        0,
        objectMapper.readTree(assignmentJson),
        objectMapper.readTree(scopeJson),
        objectMapper.readTree(targetJson),
        objectMapper.readTree(resolutionJson)
    )
}
