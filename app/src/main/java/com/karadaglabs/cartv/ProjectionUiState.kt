package com.karadaglabs.cartv

enum class ProjectionMode {
    LIST,
    PLAYING
}

data class ProjectionBindPlan(
    val shouldBindSurface: Boolean,
    val shouldSetMediaItem: Boolean,
    val shouldPlay: Boolean,
    val mediaId: String?,
    val streamUrl: String?,
    val title: String?,
    val reason: String
)

data class ProjectionUiState(
    val channels: List<IptvChannel> = emptyList(),
    val selectedId: Int? = null,
    val lastSelectedId: Int? = null,
    val surfaceReady: Boolean = false,
    val hostAvailable: Boolean = true,
    val videoFocusGranted: Boolean = false,
    val mode: ProjectionMode = ProjectionMode.LIST,
    val loading: Boolean = false,
    val errorMessage: String = ""
) {

    val selected: IptvChannel?
        get() = channels.firstOrNull { it.id == selectedId }

    val statusText: String
        get() = when {
            !hostAvailable ->
                "Projection host yok"

            loading && channels.isEmpty() ->
                "Kanallar yükleniyor..."

            errorMessage.isNotBlank() && channels.isEmpty() ->
                errorMessage

            selected != null && mode == ProjectionMode.PLAYING ->
                "Oynatılıyor: ${selected?.name}"

            selected != null ->
                "Seçili: ${selected?.name}"

            channels.isEmpty() ->
                "Kanal listesi yok"

            else ->
                "${channels.size} kanal"
        }

    fun withChannels(
        loaded: List<IptvChannel>,
        loading: Boolean = false,
        error: String = ""
    ): ProjectionUiState {
        val next = copy(
            channels = loaded,
            loading = loading,
            errorMessage = error
        )

        return if (next.selected != null) {
            next
        } else {
            next.resolveInitial()
        }
    }

    fun resolveInitial(): ProjectionUiState {
        if (channels.isEmpty()) {
            return copy(
                selectedId = null,
                mode = ProjectionMode.LIST,
                surfaceReady = false
            )
        }

        val restored = lastSelectedId?.let { id ->
            channels.firstOrNull { channel -> channel.id == id }
        }
        val channel = restored ?: channels.first()

        return copy(
            selectedId = channel.id,
            lastSelectedId = channel.id,
            mode = ProjectionMode.LIST
        )
    }

    fun select(channelId: Int): ProjectionUiState {
        val channel = channels.firstOrNull { it.id == channelId }
            ?: return this

        return copy(
            selectedId = channel.id,
            lastSelectedId = channel.id,
            mode = ProjectionMode.PLAYING
        )
    }

    fun showList(): ProjectionUiState {
        return copy(mode = ProjectionMode.LIST)
    }

    fun surfaceChanged(ready: Boolean): ProjectionUiState {
        if (!hostAvailable) {
            return copy(surfaceReady = false)
        }

        return copy(surfaceReady = ready)
    }

    fun hostUnavailable(): ProjectionUiState {
        return copy(
            hostAvailable = false,
            mode = ProjectionMode.LIST,
            surfaceReady = false,
            videoFocusGranted = false,
            loading = false
        )
    }

    fun videoFocusResult(granted: Boolean): ProjectionUiState {
        return copy(videoFocusGranted = granted)
    }

    fun bindPlan(): ProjectionBindPlan {
        if (!hostAvailable) {
            return ProjectionBindPlan(
                shouldBindSurface = false,
                shouldSetMediaItem = false,
                shouldPlay = false,
                mediaId = null,
                streamUrl = null,
                title = null,
                reason = "host_unavailable"
            )
        }

        val channel = selected

        if (channel == null) {
            return ProjectionBindPlan(
                shouldBindSurface = false,
                shouldSetMediaItem = false,
                shouldPlay = false,
                mediaId = null,
                streamUrl = null,
                title = null,
                reason = "no_current_media_item"
            )
        }

        if (mode != ProjectionMode.PLAYING) {
            return ProjectionBindPlan(
                shouldBindSurface = false,
                shouldSetMediaItem = true,
                shouldPlay = false,
                mediaId = channel.mediaId,
                streamUrl = channel.streamUrl,
                title = channel.name,
                reason = "prepared_waiting_for_surface"
            )
        }

        if (!surfaceReady) {
            return ProjectionBindPlan(
                shouldBindSurface = true,
                shouldSetMediaItem = true,
                shouldPlay = false,
                mediaId = channel.mediaId,
                streamUrl = channel.streamUrl,
                title = channel.name,
                reason = "surface_not_ready"
            )
        }

        return ProjectionBindPlan(
            shouldBindSurface = true,
            shouldSetMediaItem = true,
            shouldPlay = true,
            mediaId = channel.mediaId,
            streamUrl = channel.streamUrl,
            title = channel.name,
            reason = "ready"
        )
    }
}
