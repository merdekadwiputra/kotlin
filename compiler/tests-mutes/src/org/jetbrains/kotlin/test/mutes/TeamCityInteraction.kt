package org.jetbrains.kotlin.test.mutes

import com.fasterxml.jackson.module.kotlin.readValue
import khttp.responses.Response
import khttp.structures.authorization.Authorization

internal const val TAG = "[MUTED-BY-CSVFILE]"
private val headers = mapOf("Content-type" to "application/json", "Accept" to "application/json")
private val authUser = object : Authorization {
    override val header = "Authorization" to "Bearer ${System.getProperty("org.jetbrains.kotlin.test.mutes.buildserver.token")}"
}


internal fun getMutedTestsOnTeamcityForRootProject(): List<MuteTestJson> {
    val projectScopeId = Scope.COMMON.id

    val params = mapOf(
        "locator" to "project:(id:$projectScopeId)",
        "fields" to "mute(id,assignment(text),scope(project(id),buildTypes(buildType(id))),target(tests(test(name))),resolution)"
    )

    val response = khttp.get("https://buildserver.labs.intellij.net/app/rest/mutes", headers, params, auth = authUser)
    checkResponseAndLog(response)

    val alreadyMutedTestsOnTeamCity = objectMapper.readTree(response.text).get("mute")
        .filter { jn -> jn.get("assignment").get("text").textValue().startsWith(TAG) }

    return objectMapper.readValue(alreadyMutedTestsOnTeamCity.toString())
}

internal fun uploadMutedTests(uploadMap: Map<String, MuteTestJson>) {
    for ((_, muteTestJson) in uploadMap) {
        val response = khttp.post(
            "https://buildserver.labs.intellij.net/app/rest/mutes",
            headers = headers,
            data = objectMapper.writeValueAsString(muteTestJson),
            auth = authUser
        )
        checkResponseAndLog(response)
    }
}

internal fun deleteMutedTests(deleteMap: Map<String, MuteTestJson>) {
    for ((_, muteTestJson) in deleteMap) {
        val response = khttp.delete(
            "https://buildserver.labs.intellij.net/app/rest/mutes/id:${muteTestJson.id}",
            headers = headers,
            auth = authUser
        )
        checkResponseAndLog(response)
    }
}

private fun checkResponseAndLog(response: Response) {
    val isResponseBad = response.connection.responseCode / 100 != 2
    if (isResponseBad) {
        throw Exception(
            "${response.request.method}-request to ${response.request.url} failed:\n" +
                    "${response.text}\n" +
                    "${response.request.data ?: ""}"
        )
    }
}
