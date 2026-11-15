package com.subtracks.data.listenbrainz

/** Records every call; [failNext] makes the next submission throw, [validation] answers token checks. */
class FakeListenBrainzApi : ListenBrainzApi {
    var validation = TokenValidation(true, "listener")
    var validationFailure: ListenBrainzException? = null
    val failNext = ArrayDeque<ListenBrainzException>()

    val playingNow = mutableListOf<ListenTrack>()
    val singles = mutableListOf<Listen>()
    val imports = mutableListOf<List<Listen>>()
    val tokens = mutableListOf<String>()

    override suspend fun validateToken(
        root: String,
        token: String,
    ): TokenValidation {
        validationFailure?.let { throw it }
        return validation
    }

    override suspend fun playingNow(
        root: String,
        token: String,
        track: ListenTrack,
    ) {
        submit(token)
        playingNow += track
    }

    override suspend fun single(
        root: String,
        token: String,
        listen: Listen,
    ) {
        submit(token)
        singles += listen
    }

    override suspend fun import(
        root: String,
        token: String,
        listens: List<Listen>,
    ) {
        submit(token)
        imports += listens
    }

    private fun submit(token: String) {
        tokens += token
        failNext.removeFirstOrNull()?.let { throw it }
    }
}
