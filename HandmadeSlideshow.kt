package com.diewland.hmslideshow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import java.io.File

class HandmadeSlideshow constructor(ctx: Context,
                                    rootView: LinearLayout,
                                    private val mediaList: ArrayList<String> = arrayListOf(),
                                    muteVideo: Boolean = false) {

    private val TAG = "HMSLIDESHOW"
    private val TYPE_IMAGE = "TYPE_IMAGE"
    private val TYPE_VIDEO = "TYPE_VIDEO"
    private val EXT_IMAGE = arrayListOf("jpg", "jpeg", "png", "gif")
    private val EXT_VIDEO = arrayListOf("mp4")
    private val EXT_GIF = arrayListOf("gif")

    // exo player
    val player = SimpleExoPlayer.Builder(ctx).build()

    // views
    val imageView = ImageView(ctx)
    val videoView = PlayerView(ctx)
    val webView = WebView(ctx)

    // config
    private var photoDelay:Long = 60 // seconds

    // app state
    private var mediaIndex = 0
    private var mediaType = TYPE_IMAGE
    private var isPlaying = false

    // module thread
    private var handler = Handler()

    // image thread
    private var playNextImage: Runnable
    private var bmp: Bitmap? = null

    init {
        // TODO show read-internal-storage permission dialog ( if required )

        // add media views to root
        rootView.removeAllViews()
        rootView.addView(imageView)
        rootView.addView(videoView)
        rootView.addView(webView)

        // set up layout params
        imageView.layoutParams = getLLParams()
        videoView.layoutParams = getLLParams()
        webView.layoutParams = getLLParams()

        // hide all
        hideAllViews()

        // setup exo player
        videoView.player = player       // set player to video view
        videoView.useController = false // hide video controller
        player.playWhenReady = true     // auto play when load media done
        if (muteVideo) player.setVolume(0f)
        // ((videoView.videoSurfaceView) as SurfaceView).setZOrderOnTop(true) // remove dim from video
        /*
        // hide "Can't play this video" message
        videoView.setOnErrorListener { mp, what, extra ->
            Log.d(TAG, "--- onErrorListener ---")
            Log.d(TAG, "mp: $mp")
            Log.d(TAG, "what: $what")
            Log.d(TAG, "extra: $extra")
            restart()
            return@setOnErrorListener true
        }
        */

        // play next slide when image/video play done
        playNextImage = Runnable {
            if (bmp != null) {
                bmp!!.recycle()
                bmp = null
            }
            next()
        }
        player.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) next()
            }
        })
    }

    /* ---------- CONTROL SLIDESHOW ---------- */

    fun start() {
        isPlaying = true
        play()
    }

    fun stop() {
        isPlaying = false
        when (mediaType) {
            TYPE_IMAGE -> {
                handler.removeCallbacks(playNextImage)
            }
            TYPE_VIDEO -> {
                if (player.isPlaying) player.stop()
            }
        }
        // hide all
        hideAllViews()
        // release thread
        handler.removeCallbacksAndMessages(null)
    }

    /*
    fun restart() {
        Log.d(TAG, "restart slideshow in 1 second")
        handler.postDelayed({
            stop()
            Thread.sleep(1_000)
            start()
        }, 1_000)
    }
    */

    fun destroy() {
        player.release()
    }

    /* ---------- UPDATE SLIDESHOW ---------- */

    fun addMedia(mediaPath: String) {
        mediaList.add(mediaPath)
    }

    fun updateMedia(newMediaList: ArrayList<String>) {
        clearMedia()
        for (m in newMediaList) {
            mediaList.add(m)
        }
    }

    fun clearMedia() {
        mediaIndex = 0
        mediaList.clear()
    }

    fun setPhotoDelay(delay: Long) {
        photoDelay = delay
    }

    /* ---------- BIND APP EVENTS ---------- */

    fun onResume() {
        if (!isPlaying) start()
    }

    fun onPause() {
        if (isPlaying) stop()
    }

    fun onDestroy() {
        destroy()
    }

    /* ---------- UTILITIES ---------- */

    fun isEmptyMediaList(): Boolean {
        return mediaList.size == 0
    }

    fun checkImageExt(ext: String): Boolean {
        return EXT_IMAGE.contains(ext.toLowerCase())
    }

    fun checkVideoExt(ext: String): Boolean {
        return EXT_VIDEO.contains(ext.toLowerCase())
    }

    fun checkGifExt(ext: String): Boolean {
        return EXT_GIF.contains(ext.toLowerCase())
    }

    fun renderHTML(html: String = "") {
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    fun renderHTMLImage(path: String) {
        val html = """
        |<html>
        |    <head>
        |        <style type="text/css">
        |            html, body { padding: 0px; margin: 0px; }
        |            img { width: 100%; }
        |        </style>
        |    </head>
        |    <body>
        |        <img src="file:///$path">
        |    </body>
        |</html>
        """.trimMargin()
        renderHTML(html)
    }

    /* ---------- INTERNAL FUNCTION(S) ---------- */

    private fun play() {
        if (mediaList.size == 0) return

        // build f
        val filePath = mediaList[mediaIndex]
        val f = File(filePath)

        // file not found
        if (!f.exists()) {
            Log.d(TAG, "#$mediaIndex [SKIP] $filePath <-- File not found")
        }

        // play image
        else if (checkImageExt(f.extension)) {
            Log.d(TAG, "#$mediaIndex [PASS] $filePath")
            when {
                checkGifExt(f.extension) -> playGif(f)
                else -> playImage(f)
            }

            // do not refresh if have one media
            if (mediaList.size == 1) return

            handler.postDelayed(playNextImage, photoDelay * 1000)
        }

        // play video
        else if (checkVideoExt(f.extension)) {
            Log.d(TAG, "#$mediaIndex [PASS] $filePath")
            playVideo(f)
        }

        // extension does not support
        else {
            Log.d(TAG, "#$mediaIndex [SKIP] $filePath <-- Extension does not support")
        }
    }

    // control flow
    private fun next() {
        if (mediaIndex < mediaList.size-1) {
            mediaIndex++
        }
        else {
            mediaIndex = 0
        }
        play()
    }

    private fun playImage(f: File) {
        mediaType = TYPE_IMAGE

        // toggle media view
        showImageView()

        // set image
        bmp = BitmapFactory.decodeFile(f.absolutePath)
        imageView.setImageBitmap(bmp)
    }

    private fun playVideo(f: File) {
        mediaType = TYPE_VIDEO

        // toggle media view
        showVideoView()

        // play video
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
        player.prepare() // video will play automatically from playWhenReady setting
    }

    private fun playGif(f: File) {
        mediaType = TYPE_IMAGE

        // toggle media view
        showWebView()

        // set image
        renderHTMLImage(f.absolutePath)
    }

    private fun getLLParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    // toggle views
    private fun hideAllViews() {
        hideImage()
        hideVideo()
        hideWeb()
    }
    private fun showImageView() {
        showImage()
        hideVideo()
        hideWeb()
    }
    private fun showVideoView() {
        hideImage()
        showVideo()
        hideWeb()
    }
    private fun showWebView() {
        hideImage()
        hideVideo()
        showWeb()
    }
    private fun showImage() { imageView.visibility = View.VISIBLE }
    private fun hideImage() { imageView.visibility = View.GONE }
    private fun showVideo() {
        videoView.visibility = View.VISIBLE
        videoView.videoSurfaceView?.visibility = View.VISIBLE
    }
    private fun hideVideo() {
        videoView.visibility = View.GONE
        videoView.videoSurfaceView?.visibility = View.GONE
    }
    private fun showWeb() { webView.visibility = View.VISIBLE }
    private fun hideWeb() {
        webView.visibility = View.GONE
        renderHTML("")
    }

}
