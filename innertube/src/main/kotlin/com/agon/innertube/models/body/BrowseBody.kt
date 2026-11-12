package com.agon.innertube.models.body

import com.agon.innertube.models.Context
import com.agon.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
