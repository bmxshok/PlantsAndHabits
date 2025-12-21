package com.example.plantsandhabits

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Упрощенный создатель таймлапсов из фотографий
 * Использует MediaMuxer и MediaCodec для создания видео
 */
object TimelapseCreator {
    private const val TAG = "TimelapseCreator"
    private const val MIME_TYPE = "video/avc" // H.264
    private const val FRAME_RATE = 30 // 30 кадров в секунду для плавности
    private const val I_FRAME_INTERVAL = 1
    private const val BIT_RATE = 2000000 // 2 Mbps
    private const val VIDEO_WIDTH = 1280
    private const val VIDEO_HEIGHT = 720
    private const val TIMEOUT_USEC = 10000L
    
    // Параметры для плавных переходов
    private const val SECONDS_PER_PHOTO = 3.0 // 3 секунды на каждую фотографию
    private const val FADE_DURATION_FRAMES = 15 // Количество кадров для fade эффекта (0.5 секунды при 30 FPS)

    /**
     * Создает таймлапс видео из списка фотографий
     * @param context Контекст приложения
     * @param photoPaths Список путей к фотографиям (должны быть отсортированы по дате)
     * @param outputPath Путь для сохранения выходного видео файла
     * @param onProgress Callback для обновления прогресса (current, total)
     * @return true если успешно, false в случае ошибки
     */
    suspend fun createTimelapse(
        context: Context,
        photoPaths: List<String>,
        outputPath: String,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (photoPaths.isEmpty()) {
            Log.e(TAG, "No photos provided")
            return@withContext false
        }

        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null

        try {
            // Создаем директорию для выходного файла если не существует
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            // Инициализируем MediaMuxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Создаем MediaCodec для кодирования видео
            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            val format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            // Получаем трек индекс от муксера
            var videoTrackIndex = -1
            var encoderOutputBufferInfo = MediaCodec.BufferInfo()

            // Загружаем все фотографии заранее для плавных переходов
            val bitmaps = mutableListOf<Bitmap?>()
            for (photoPath in photoPaths) {
                val bitmap = loadAndScaleBitmap(photoPath, VIDEO_WIDTH, VIDEO_HEIGHT)
                bitmaps.add(bitmap)
            }

            // Вычисляем общее количество кадров
            val framesPerPhoto = (SECONDS_PER_PHOTO * FRAME_RATE).toInt()
            var frameIndex = 0

            // Обрабатываем каждую фотографию с плавными переходами
            for (photoIndex in photoPaths.indices) {
                val currentBitmap = bitmaps[photoIndex]
                val nextBitmap = if (photoIndex < bitmaps.size - 1) bitmaps[photoIndex + 1] else null

                if (currentBitmap == null) {
                    Log.w(TAG, "Failed to load bitmap: ${photoPaths[photoIndex]}")
                    continue
                }

                // Рендерим кадры для текущей фотографии
                for (frameInPhoto in 0 until framesPerPhoto) {
                    // Определяем, нужно ли делать fade переход
                    val isFadeOut = nextBitmap != null && frameInPhoto >= framesPerPhoto - FADE_DURATION_FRAMES
                    
                    if (isFadeOut && nextBitmap != null) {
                        // Fade out текущей фотографии и fade in следующей
                        val fadeProgress = (frameInPhoto - (framesPerPhoto - FADE_DURATION_FRAMES)).toFloat() / FADE_DURATION_FRAMES
                        renderFadeTransition(inputSurface!!, currentBitmap, nextBitmap, fadeProgress, VIDEO_WIDTH, VIDEO_HEIGHT)
                    } else {
                        // Обычный кадр без эффектов
                        renderFrameToSurface(inputSurface!!, currentBitmap, VIDEO_WIDTH, VIDEO_HEIGHT, 1.0f)
                    }

                    // Кодируем кадр - дренируем encoder после рендеринга
                    var outputBufferIndex: Int
                    do {
                        outputBufferIndex = encoder.dequeueOutputBuffer(encoderOutputBufferInfo, 0) // Не ждем, просто проверяем
                        if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val newTrackIndex = muxer.addTrack(encoder.outputFormat)
                            if (videoTrackIndex < 0 && newTrackIndex >= 0) {
                                videoTrackIndex = newTrackIndex
                                muxer.start()
                            }
                        } else if (outputBufferIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && (encoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                // Используем правильное время для каждого кадра
                                val frameTimeUs = (frameIndex * 1000000L) / FRAME_RATE
                                encoderOutputBufferInfo.presentationTimeUs = frameTimeUs
                                if (videoTrackIndex >= 0) {
                                    muxer.writeSampleData(videoTrackIndex, outputBuffer, encoderOutputBufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
                    } while (outputBufferIndex >= 0)

                    frameIndex++
                    if (frameIndex % FRAME_RATE == 0) { // Обновляем прогресс раз в секунду
                        onProgress(photoIndex + 1, photoPaths.size)
                    }
                }
            }

            // Освобождаем память
            bitmaps.forEach { it?.recycle() }

            // Завершаем кодирование - сигнализируем EOS
            try {
                encoder.signalEndOfInputStream()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "EOS already signaled or encoder in wrong state", e)
            }

            // Дренируем все оставшиеся буферы после EOS
            var eosReceived = false
            var lastFrameTimeUs: Long = 0
            while (!eosReceived) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(encoderOutputBufferInfo, TIMEOUT_USEC)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Продолжаем ждать
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newTrackIndex = muxer.addTrack(encoder.outputFormat)
                        if (videoTrackIndex < 0 && newTrackIndex >= 0) {
                            videoTrackIndex = newTrackIndex
                            muxer.start()
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && (encoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            // Используем правильное время для последнего кадра
                            val frameTimeUs = (frameIndex * 1000000L) / FRAME_RATE
                            encoderOutputBufferInfo.presentationTimeUs = frameTimeUs
                            lastFrameTimeUs = frameTimeUs
                            if (videoTrackIndex >= 0) {
                                muxer.writeSampleData(videoTrackIndex, outputBuffer, encoderOutputBufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        if ((encoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eosReceived = true
                        }
                    }
                }
            }
            
            // Вычисляем правильную длительность видео
            // Длительность = количество кадров * время одного кадра
            val actualVideoDurationUs = (frameIndex * 1000000L) / FRAME_RATE

            muxer?.stop()
            muxer?.release()
            encoder?.stop()
            encoder?.release()
            inputSurface?.release()

            Log.d(TAG, "Timelapse created successfully: $outputPath")
            
            // Пытаемся добавить музыку к видео
            try {
                Log.d(TAG, "Attempting to add music. Video duration: ${actualVideoDurationUs / 1000000} seconds")
                val videoWithAudioPath = outputPath.replace(".mp4", "_temp_audio.mp4")
                val successWithAudio = addBackgroundMusic(context, outputPath, videoWithAudioPath, actualVideoDurationUs)
                
                if (successWithAudio && File(videoWithAudioPath).exists() && File(videoWithAudioPath).length() > 0) {
                    // Удаляем временный файл без музыки и переименовываем финальный
                    val originalSize = File(outputPath).length()
                    val newSize = File(videoWithAudioPath).length()
                    Log.d(TAG, "Original video size: $originalSize bytes, With audio: $newSize bytes")
                    File(outputPath).delete()
                    File(videoWithAudioPath).renameTo(File(outputPath))
                    Log.d(TAG, "Timelapse with music created successfully: $outputPath")
                } else {
                    Log.w(TAG, "Failed to add music or file not created. Video saved without audio: $outputPath")
                    // Удаляем временный файл если он был создан
                    if (File(videoWithAudioPath).exists()) {
                        File(videoWithAudioPath).delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not add music, video created without audio", e)
                e.printStackTrace()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating timelapse", e)
            e.printStackTrace()
            false
        } finally {
            // Ресурсы уже освобождены выше
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        presentationTimeUs: Long,
        onTrackAdded: ((Int) -> Unit)?
    ) {
        var outputBufferIndex: Int
        do {
            outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // Нет доступных буферов
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newTrackIndex = muxer.addTrack(encoder.outputFormat)
                onTrackAdded?.invoke(newTrackIndex)
            } else if (outputBufferIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    bufferInfo.presentationTimeUs = presentationTimeUs
                    if (trackIndex >= 0) {
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false)
            }
        } while (outputBufferIndex >= 0)
    }

    private fun loadAndScaleBitmap(photoPath: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(photoPath, options)

            // Вычисляем коэффициент масштабирования
            val scaleFactor = minOf(
                options.outWidth.toFloat() / targetWidth,
                options.outHeight.toFloat() / targetHeight
            )

            options.inJustDecodeBounds = false
            options.inSampleSize = if (scaleFactor > 1) scaleFactor.toInt() else 1

            val bitmap = BitmapFactory.decodeFile(photoPath, options)
            if (bitmap != null) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: $photoPath", e)
            null
        }
    }

    private fun renderFrameToSurface(surface: Surface, bitmap: Bitmap, width: Int, height: Int, alpha: Float = 1.0f) {
        try {
            val canvas = surface.lockCanvas(null)
            canvas?.let {
                // Очищаем canvas
                it.drawRGB(0, 0, 0)
                // Устанавливаем прозрачность
                val paint = android.graphics.Paint().apply {
                    this.alpha = (alpha * 255).toInt()
                }
                // Рисуем bitmap по центру
                val left = (width - bitmap.width) / 2f
                val top = (height - bitmap.height) / 2f
                it.drawBitmap(bitmap, left, top, paint)
                surface.unlockCanvasAndPost(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering frame to surface", e)
        }
    }

    private fun renderFadeTransition(
        surface: Surface,
        currentBitmap: Bitmap,
        nextBitmap: Bitmap,
        fadeProgress: Float, // 0.0 = текущая, 1.0 = следующая
        width: Int,
        height: Int
    ) {
        try {
            val canvas = surface.lockCanvas(null)
            canvas?.let {
                // Очищаем canvas
                it.drawRGB(0, 0, 0)
                
                val left = (width - currentBitmap.width) / 2f
                val top = (height - currentBitmap.height) / 2f
                
                // Рисуем текущую фотографию с fade out
                val currentPaint = android.graphics.Paint().apply {
                    alpha = ((1.0f - fadeProgress) * 255).toInt()
                }
                it.drawBitmap(currentBitmap, left, top, currentPaint)
                
                // Рисуем следующую фотографию с fade in
                val nextPaint = android.graphics.Paint().apply {
                    alpha = (fadeProgress * 255).toInt()
                }
                it.drawBitmap(nextBitmap, left, top, nextPaint)
                
                surface.unlockCanvasAndPost(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering fade transition", e)
        }
    }

    /**
     * Добавляет фоновую музыку к видео из MP3 файла в ресурсах
     * Всегда использует background_music.mp3 из папки res/raw/
     */
    private fun addBackgroundMusic(
        context: Context,
        videoPath: String,
        outputPath: String,
        videoDurationUs: Long
    ): Boolean {
        // Всегда используем MP3 файл из ресурсов
        val audioResourceId = context.resources.getIdentifier("background_music", "raw", context.packageName)
        if (audioResourceId != 0) {
            Log.d(TAG, "Found background_music.mp3 in resources, using it")
            return addMusicFromResource(context, videoPath, outputPath, videoDurationUs, audioResourceId)
        }
        
        // Если файл не найден, выводим ошибку
        Log.e(TAG, "background_music.mp3 not found in res/raw/ folder. Please add the file.")
        return false
    }
    
    /**
     * Добавляет музыку из AAC файла в ресурсах
     */
    private fun addMusicFromResource(
        context: Context,
        videoPath: String,
        outputPath: String,
        videoDurationUs: Long,
        audioResourceId: Int
    ): Boolean {
        return try {
            Log.d(TAG, "Loading video from: $videoPath")
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)
            
            Log.d(TAG, "Loading audio from resource ID: $audioResourceId")
            val audioExtractor = MediaExtractor()
            val audioUri = android.net.Uri.parse("android.resource://${context.packageName}/$audioResourceId")
            audioExtractor.setDataSource(context, audioUri, null)
            
            Log.d(TAG, "Video tracks: ${videoExtractor.trackCount}, Audio tracks: ${audioExtractor.trackCount}")
            
            val videoTrackIndex = findVideoTrack(videoExtractor)
            val audioTrackIndex = findAudioTrack(audioExtractor)
            
            if (videoTrackIndex < 0) {
                Log.e(TAG, "Video track not found in video file")
                videoExtractor.release()
                audioExtractor.release()
                return false
            }
            
            if (audioTrackIndex < 0) {
                Log.e(TAG, "Audio track not found in MP3 file")
                videoExtractor.release()
                audioExtractor.release()
                return false
            }
            
            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)
            
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
            
            Log.d(TAG, "Video format: ${videoFormat.getString(MediaFormat.KEY_MIME)}")
            Log.d(TAG, "Audio format: ${audioFormat.getString(MediaFormat.KEY_MIME)}")
            
            // Проверяем формат аудио - AAC должен поддерживаться напрямую
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME)
            if (audioMime?.startsWith("audio/mpeg") == true || audioMime?.startsWith("audio/mp3") == true) {
                Log.e(TAG, "MP3 format detected. Please convert to AAC format first.")
                videoExtractor.release()
                audioExtractor.release()
                return false
            }
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTrackIndexMuxer = muxer.addTrack(videoFormat)
            val audioTrackIndexMuxer = muxer.addTrack(audioFormat)
            muxer.start()
            
            Log.d(TAG, "Muxer started. Video track index: $videoTrackIndexMuxer, Audio track index: $audioTrackIndexMuxer")
            
            // Копируем видео
            val videoBuffer = ByteBuffer.allocate(1024 * 1024)
            val videoBufferInfo = android.media.MediaCodec.BufferInfo()
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            var videoSamples = 0
            while (true) {
                val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                if (sampleSize < 0) break
                
                videoBufferInfo.offset = 0
                videoBufferInfo.size = sampleSize
                videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME
                
                muxer.writeSampleData(videoTrackIndexMuxer, videoBuffer, videoBufferInfo)
                videoSamples++
                if (!videoExtractor.advance()) break
            }
            Log.d(TAG, "Copied $videoSamples video samples")
            
            // Копируем аудио (AAC формат поддерживается напрямую)
            val audioBuffer = ByteBuffer.allocate(1024 * 1024)
            val audioBufferInfo = android.media.MediaCodec.BufferInfo()
            var audioTimeOffset: Long = 0
            val audioDuration = audioFormat.getLong(MediaFormat.KEY_DURATION)
            
            Log.d(TAG, "Audio duration: ${audioDuration / 1000000} seconds, Video duration: ${videoDurationUs / 1000000} seconds")
            
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            var audioSamples = 0
            while (audioTimeOffset < videoDurationUs) {
                val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                if (sampleSize < 0) {
                    // Зацикливаем аудио если видео длиннее
                    audioTimeOffset += audioDuration
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    Log.d(TAG, "Looping audio. New offset: ${audioTimeOffset / 1000000} seconds")
                    continue
                }
                
                audioBufferInfo.offset = 0
                audioBufferInfo.size = sampleSize
                audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime + audioTimeOffset
                audioBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME
                
                if (audioBufferInfo.presentationTimeUs >= videoDurationUs) {
                    Log.d(TAG, "Audio reached video duration, stopping")
                    break
                }
                
                muxer.writeSampleData(audioTrackIndexMuxer, audioBuffer, audioBufferInfo)
                audioSamples++
                
                if (!audioExtractor.advance()) {
                    // Зацикливаем аудио
                    audioTimeOffset += audioDuration
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    Log.d(TAG, "Audio track ended, looping. New offset: ${audioTimeOffset / 1000000} seconds")
                }
            }
            
            Log.d(TAG, "Copied $audioSamples audio samples")

            
            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
            
            Log.d(TAG, "Music added from resource successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding music from resource", e)
            false
        }
    }
    
    /**
     * Генерирует и добавляет простую тихую мелодию
     */
    private fun generateAndAddMusic(
        context: Context,
        videoPath: String,
        outputPath: String,
        videoDurationUs: Long
    ): Boolean {
        return try {
            // Создаем простой аудио трек (тихая фоновая музыка)
            // Для упрощения используем MediaCodec для генерации простого тона
            val audioSampleRate = 44100
            val audioBitrate = 128000
            val audioChannelCount = 2
            
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)
            
            val videoTrackIndex = findVideoTrack(videoExtractor)
            if (videoTrackIndex < 0) {
                Log.e(TAG, "No video track found")
                return false
            }
            
            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTrackIndexMuxer = muxer.addTrack(videoFormat)
            
            // Создаем простой аудио формат
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                audioSampleRate,
                audioChannelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
            }
            
            // Генерируем простой аудио трек (тихая фоновая музыка)
            val audioTrackIndexMuxer = muxer.addTrack(audioFormat)
            muxer.start()
            
            // Копируем видео
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME
                
                muxer.writeSampleData(videoTrackIndexMuxer, buffer, bufferInfo)
                if (!videoExtractor.advance()) break
            }
            
            // Генерируем простой аудио трек (тихий тон)
            generateSimpleAudio(muxer, audioTrackIndexMuxer, videoDurationUs, audioSampleRate, audioChannelCount)
            
            muxer.stop()
            muxer.release()
            videoExtractor.release()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding background music", e)
            false
        }
    }
    
    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return i
            }
        }
        return -1
    }
    
    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }
    
    private fun generateSimpleAudio(
        muxer: MediaMuxer,
        trackIndex: Int,
        durationUs: Long,
        sampleRate: Int,
        channelCount: Int
    ) {
        // Генерируем простой тихий тон (спокойная фоновая музыка)
        // Используем AAC encoder для создания аудио
        val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        
        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()
        
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        
        val samplesPerFrame = 1024
        val totalSamples = (durationUs * sampleRate / 1000000L).toInt()
        var sampleIndex = 0
        var presentationTimeUs: Long = 0
        
        while (sampleIndex < totalSamples) {
            val inputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferIndex >= 0) {
                val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                
                val samplesToGenerate = minOf(samplesPerFrame, totalSamples - sampleIndex)
                val buffer = ByteBuffer.allocate(samplesToGenerate * 2 * channelCount)
                buffer.order(java.nio.ByteOrder.nativeOrder())
                
                for (i in 0 until samplesToGenerate) {
                    // Генерируем простой синусоидальный тон (тихая фоновая музыка)
                    // Используем более сложную мелодию с несколькими частотами
                    val baseFreq = 220.0
                    val freq1 = baseFreq + 55.0 * Math.sin(sampleIndex * 2 * Math.PI / (sampleRate * 4))
                    val freq2 = baseFreq * 1.5 + 30.0 * Math.sin(sampleIndex * 2 * Math.PI / (sampleRate * 6))
                    
                    val amplitude = 0.3 // Увеличиваем громкость до 30%
                    val sample1 = (Math.sin(2 * Math.PI * freq1 * sampleIndex / sampleRate) * amplitude * Short.MAX_VALUE).toInt()
                    val sample2 = (Math.sin(2 * Math.PI * freq2 * sampleIndex / sampleRate) * amplitude * 0.5 * Short.MAX_VALUE).toInt()
                    val combinedSample = (sample1 + sample2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    
                    // Записываем для каждого канала (стерео)
                    for (channel in 0 until channelCount) {
                        buffer.putShort(combinedSample)
                    }
                    
                    sampleIndex++
                }
                
                buffer.flip()
                inputBuffer?.put(buffer)
                audioEncoder.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    buffer.remaining(),
                    presentationTimeUs,
                    0
                )
                presentationTimeUs += samplesToGenerate * 1000000L / sampleRate
            }
            
            // Дренируем encoder во время генерации
            var outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                val outputBuffer = audioEncoder.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                }
                audioEncoder.releaseOutputBuffer(outputBufferIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
                outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }
        
        // Завершаем кодирование
        try {
            audioEncoder.signalEndOfInputStream()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "EOS already signaled for audio encoder", e)
        }
        
        // Дренируем все оставшиеся буферы
        var eosReceived = false
        while (!eosReceived) {
            val outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Продолжаем ждать
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Формат уже установлен
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = audioEncoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    audioEncoder.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eosReceived = true
                    }
                }
            }
        }
        
        audioEncoder.stop()
        audioEncoder.release()
        Log.d(TAG, "Audio generation completed")
    }
    
    /**
     * Конвертирует MP3 в AAC для совместимости с MediaMuxer
     */
    private fun convertMp3ToAac(
        audioExtractor: MediaExtractor,
        audioTrackIndex: Int,
        muxer: MediaMuxer,
        audioTrackIndexMuxer: Int,
        videoDurationUs: Long
    ) {
        // Создаем декодер для MP3
        val inputFormat = audioExtractor.getTrackFormat(audioTrackIndex)
        val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
        
        // Создаем энкодер для AAC
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        
        val outputFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        
        val inputBufferInfo = MediaCodec.BufferInfo()
        val outputBufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(1024 * 1024)
        
        var audioTimeOffset: Long = 0
        val audioDuration = inputFormat.getLong(MediaFormat.KEY_DURATION)
        var decoderOutputEOS = false
        var encoderOutputEOS = false
        
        audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        
        while (!encoderOutputEOS && audioTimeOffset < videoDurationUs) {
            // Декодируем MP3
            if (!decoderOutputEOS) {
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    
                    if (sampleSize < 0) {
                        // Конец входного потока
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        decoderOutputEOS = true
                    } else {
                        inputBuffer?.put(buffer)
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            audioExtractor.sampleTime,
                            audioExtractor.sampleFlags
                        )
                        audioExtractor.advance()
                    }
                }
            }
            
            // Получаем декодированные данные
            var outputBufferIndex = decoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_USEC)
            while (outputBufferIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                
                if (outputBuffer != null && outputBufferInfo.size > 0) {
                    // Кодируем в AAC
                    val encoderInputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (encoderInputIndex >= 0) {
                        val encoderInputBuffer = encoder.getInputBuffer(encoderInputIndex)
                        encoderInputBuffer?.put(outputBuffer)
                        encoder.queueInputBuffer(
                            encoderInputIndex,
                            0,
                            outputBufferInfo.size,
                            outputBufferInfo.presentationTimeUs + audioTimeOffset,
                            outputBufferInfo.flags
                        )
                    }
                }
                
                decoder.releaseOutputBuffer(outputBufferIndex, false)
                
                if ((outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // Сигнализируем EOS энкодеру
                    val encoderInputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (encoderInputIndex >= 0) {
                        encoder.queueInputBuffer(
                            encoderInputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    }
                }
                
                outputBufferIndex = decoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_USEC)
            }
            
            // Получаем закодированные данные AAC
            var encoderOutputIndex = encoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_USEC)
            while (encoderOutputIndex >= 0) {
                val encoderOutputBuffer = encoder.getOutputBuffer(encoderOutputIndex)
                
                if (encoderOutputBuffer != null && outputBufferInfo.size > 0 && 
                    (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    
                    val presentationTimeUs = outputBufferInfo.presentationTimeUs
                    if (presentationTimeUs < videoDurationUs) {
                        muxer.writeSampleData(audioTrackIndexMuxer, encoderOutputBuffer, outputBufferInfo)
                    } else {
                        encoderOutputEOS = true
                        break
                    }
                }
                
                encoder.releaseOutputBuffer(encoderOutputIndex, false)
                
                if ((outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    encoderOutputEOS = true
                    break
                }
                
                encoderOutputIndex = encoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_USEC)
            }
            
            // Зацикливаем если нужно
            if (decoderOutputEOS && encoderOutputEOS && audioTimeOffset + audioDuration < videoDurationUs) {
                audioTimeOffset += audioDuration
                audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                decoderOutputEOS = false
                encoderOutputEOS = false
                Log.d(TAG, "Looping audio. New offset: ${audioTimeOffset / 1000000} seconds")
            }
        }
        
        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        
        Log.d(TAG, "MP3 to AAC conversion completed")
    }
}
