package com.peecock.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import com.peecock.innertube.Innertube
import com.peecock.innertube.models.Context
import com.peecock.innertube.models.PlayerResponse
import com.peecock.innertube.models.bodies.PlayerBody
import com.peecock.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    val response = client.post(player) {
        setBody(body)
        mask("playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
    }.body<PlayerResponse>()

    println("mediaItem requests Player response $response")

    if (response.playabilityStatus?.status == "OK") {
        response
    } else {
        val safePlayerResponse = client.post(player) {
            setBody(
                body.copy(
                    //context = Context.DefaultAgeRestrictionBypass.copy(
                    context = Context.DefaultWeb.copy(
                        thirdParty = Context.ThirdParty(
                            embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                        )
                    ),
                )
            )
            mask("playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
        }.body<PlayerResponse>()

        if (safePlayerResponse.playabilityStatus?.status != "OK") {
            return@runCatchingNonCancellable response
        }

        // Previously this substituted stream URLs from a third-party proxy
        // (watchapi.whatever.social) matched by bitrate. That domain has since
        // expired and is now a parked "domain for sale" page, so the lookup
        // always failed and surfaced as a spurious VideoIdMismatchException
        // downstream. The web/embed-bypass response above already carries its
        // own working adaptiveFormats URLs, so just use it directly.
        safePlayerResponse
    }
}
