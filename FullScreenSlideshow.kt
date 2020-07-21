package com.diewland.handmadeslideshow

import android.app.Activity
import android.graphics.Color
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

class FullScreenSlideshow (act: Activity,
                           private val screenWidth: Int=1920,   // FHD
                           private val screenHeight: Int=1080,  // FHD
                           closeCallback: (() -> Unit)? = null) {

    private var dialog: AlertDialog
    private var screen: LinearLayout = LinearLayout(act)

    // open access for load media-list from outside
    var slideshow = HandmadeSlideshow(act, screen)
    var isRunning = false

    init {
        // update view style
        screen.setBackgroundColor(Color.BLACK)

        // create fullscreen dialog
        val builder = AlertDialog.Builder(act,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen).setView(screen)
        builder.setCancelable(false)
        dialog = builder.create()

        // close when click screen
        screen.setOnClickListener {
            stop()
            closeCallback?.invoke()
        }
    }

    fun start () {
        // check duplicate
        if (!isReadyToStart()) return
        isRunning = true

        // setup full-screen
        dialog.show()
        dialog.window?.setLayout(screenWidth, screenHeight)
        screen.layoutParams.width = screenWidth
        screen.layoutParams.height = screenHeight

        // play slideshow
        slideshow.start()
    }

    fun stop () {
        // check duplicate
        if (!isRunning) return
        isRunning = false

        // stop slideshow, destroy dialog
        slideshow.stop()
        dialog.dismiss()
    }

    fun isReadyToStart (): Boolean {
        if (isRunning) return false
        if (slideshow.isEmptyMediaList()) return false
        return true
    }

}