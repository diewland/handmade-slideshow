package com.diewland.hmslideshow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.VideoView
import java.io.File

class HandmadeSlideshow constructor(ctx: Context,
                                    rootView: LinearLayout,
                                    private val mediaList: ArrayList<String> = arrayListOf(),
                                    muteVideo: Boolean = false,
                                    volume: Float = 1f,
                                    private val eventLog: ((String)->Unit)?=null) {

    private val TAG = "HMSLIDESHOW"
    private val TYPE_IMAGE = "TYPE_IMAGE"
    private val TYPE_VIDEO = "TYPE_VIDEO"
    private val EXT_IMAGE = arrayListOf("jpg", "jpeg", "png", "gif")
    private val EXT_VIDEO = arrayListOf("mp4")
    private val EXT_GIF = arrayListOf("gif")

    // views
    val imageView = ImageView(ctx)
    val videoView = VideoView(ctx)
    val webView = WebView(ctx)

    // media player
    lateinit var mediaPlayer: MediaPlayer

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
        imageView.visibility = View.GONE
        videoView.visibility = View.GONE
        webView.visibility = View.GONE

        videoView.setOnPreparedListener { mp ->
            // get media player
            mediaPlayer = mp
            // video volume
            val v = if (muteVideo) 0f else volume
            mp.setVolume(v, v)
            // play video
            videoView.start()
        }
        // remove dim from video
        videoView.setZOrderOnTop(true)
        // hide "Can't play this video" message
        videoView.setOnErrorListener { mp, what, extra ->
            l("--- onErrorListener ---")
            l("mp: $mp")
            l("what: $what")
            l("extra: $extra")
            restart()
            return@setOnErrorListener true
        }

        // play next slide when image/video play done
        playNextImage = Runnable {
            if (bmp != null) {
                bmp!!.recycle()
                bmp = null
            }
            next()
        }
        videoView.setOnCompletionListener { next() }
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
                if (videoView.isPlaying) videoView.stopPlayback()
            }
        }
        // hide all
        imageView.visibility = View.GONE
        videoView.visibility = View.GONE
        webView.visibility = View.GONE
        // release thread
        handler.removeCallbacksAndMessages(null)
    }

    fun restart() {
        l("restart slideshow in 1 second")
        handler.postDelayed({
            stop()
            Thread.sleep(1_000)
            start()
        }, 1_000)
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
            l("#$mediaIndex [SKIP] $filePath <-- File not found")
        }

        // play image
        else if (checkImageExt(f.extension)) {
            l("#$mediaIndex [PASS] $filePath")
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
            l("#$mediaIndex [PASS] $filePath")
            playVideo(f)
        }

        // extension does not support
        else {
            l("#$mediaIndex [SKIP] $filePath <-- Extension does not support")
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
        videoView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        renderHTML("")

        // set image
        bmp = BitmapFactory.decodeFile(f.absolutePath)
        imageView.setImageBitmap(bmp)
    }

    private fun playVideo(f: File) {
        mediaType = TYPE_VIDEO

        // toggle media view
        imageView.visibility = View.GONE
        videoView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        renderHTML("")

        // play video
        handler.post { // prevent ANR
            videoView.setVideoURI(Uri.fromFile(f))
            // videoView.start() --- start from setOnPreparedListener
        }
    }

    private fun playGif(f: File) {
        mediaType = TYPE_IMAGE

        // toggle media view
        imageView.visibility = View.GONE
        videoView.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // set image
        renderHTMLImage(f.absolutePath)
    }

    private fun getLLParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    /* ---------- LOG ---------- */

    private fun l(msg: String) {
        Log.d(TAG, msg)
        eventLog?.invoke(msg)
    }

}
