package fr.ensisa.bebop2controller.view;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Bebop2VideoView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "Bebop2VideoView", VIDEO_MIME_TYPE = "video/avc";
    private static final int VIDEO_DEQUEUE_TIMEOUT = 33000;

    private static int VIDEO_WIDTH = 640, VIDEO_HEIGHT = 360;

    public static void setVideoFormatDimensions(int width, int height) {
        VIDEO_WIDTH = width;
        VIDEO_HEIGHT = height;
    }

    private boolean isCodecConfigured = false;

    private ByteBuffer spsBuffer, ppsBuffer;
    private ByteBuffer[] buffers;
    private Lock readyLock;
    private MediaCodec mediaCodec;

    public Bebop2VideoView(Context context) {
        super(context);
        customInit();
    }

    public Bebop2VideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        customInit();
    }

    public Bebop2VideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        customInit();
    }

    private void customInit() {
        readyLock = new ReentrantLock();
        getHolder().addCallback(this);
    }

    public void displayFrame(ARFrame frame) {
        readyLock.lock();

        if((mediaCodec != null)) {
            if(isCodecConfigured) {
                int index = -1;

                try {
                    index = mediaCodec.dequeueInputBuffer(VIDEO_DEQUEUE_TIMEOUT);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error while dequeue input buffer");
                }

                if(index >= 0) {
                    ByteBuffer buffer;

                    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
                        buffer = mediaCodec.getInputBuffer(index);
                    else {
                        buffer = buffers[index];
                        buffer.clear();
                    }

                    if(buffer != null)
                        buffer.put(frame.getByteData(), 0, frame.getDataSize());

                    try {
                        mediaCodec.queueInputBuffer(index, 0, frame.getDataSize(), 0, 0);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error while queue input buffer");
                    }
                }
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outIndex;

            try {
                outIndex = mediaCodec.dequeueOutputBuffer(info, 0);

                while (outIndex >= 0) {
                    mediaCodec.releaseOutputBuffer(outIndex, true);
                    outIndex = mediaCodec.dequeueOutputBuffer(info, 0);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }

        readyLock.unlock();
    }

    public void configureDecoder(ARControllerCodec codec) {
        readyLock.lock();

        if(codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
            ARControllerCodec.H264 codecH264 = codec.getAsH264();
            spsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
            ppsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());
        }

        if((mediaCodec != null) && (spsBuffer != null))
            configureMediaCodec();

        readyLock.unlock();
    }

    private void configureMediaCodec() {
        mediaCodec.stop();
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setByteBuffer("csd-0", spsBuffer);
        format.setByteBuffer("csd-1", ppsBuffer);

        mediaCodec.configure(format, getHolder().getSurface(), null, 0);
        mediaCodec.start();

        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP)
            //noinspection deprecation
            buffers = mediaCodec.getInputBuffers();

        isCodecConfigured = true;
    }

    private void initMediaCodec(String type) {
        try {
            mediaCodec = MediaCodec.createDecoderByType(type);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }

        if((mediaCodec != null) && (spsBuffer != null))
            configureMediaCodec();
    }

    private void releaseMediaCodec() {
        if(mediaCodec != null) {
            if(isCodecConfigured) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            isCodecConfigured = false;
            mediaCodec = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        readyLock.lock();
        initMediaCodec(VIDEO_MIME_TYPE);
        readyLock.unlock();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        readyLock.lock();
        releaseMediaCodec();
        readyLock.unlock();
    }

}
