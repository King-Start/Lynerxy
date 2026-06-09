package com.agon.app.services

interface ActionPlaying {
    fun nextClicked()
    fun prevClicked()
    fun playClicked()
    fun onProgressChanged(progress: Int)
}
