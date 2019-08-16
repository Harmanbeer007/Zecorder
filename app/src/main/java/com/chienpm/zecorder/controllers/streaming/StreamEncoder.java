package com.chienpm.zecorder.controllers.streaming;
/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: StreamEncoder.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class StreamEncoder implements Runnable {
	private static final boolean DEBUG = false;	// TODO set false on release
	private static final String TAG = "chienpm_log";

	protected static final int TIMEOUT_USEC = 10000;	// 10[msec]
	protected static final int MSG_FRAME_AVAILABLE = 1;
	protected static final int MSG_STOP_RECORDING = 9;
	protected StreamMuxerWrapper mMuxerWrapper;

	public interface StreamEncoderListener {
		public void onPrepared(StreamEncoder encoder);
		public void onStopped(StreamEncoder encoder);
	}

	protected final Object mSync = new Object();
	/**
	 * Flag that indicate this encoder is capturing now.
	 */
    protected volatile boolean mIsCapturing;
	/**
	 * Flag that indicate the frame data will be available soon.
	 */
	private int mRequestDrain;
    /**
     * Flag to request stop capturing
     */
    protected volatile boolean mRequestStop;
    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected boolean mIsEOS;
    /**
     * Flag the indicate the muxer is running
     */
    protected boolean mMuxerStarted;
    /**
     * Track Number
     */
    protected int mTrackIndex;
    /**
     * MediaCodec instance for encoding
     */
    protected MediaCodec mMediaCodec;				// API >= 16(Android4.1.2)

	protected MediaCodec.BufferInfo mBufferInfo;		// API >= 16(Android4.1.2)

    protected final StreamEncoderListener mListener;

	protected volatile boolean mRequestPause;

    public StreamEncoder(final StreamMuxerWrapper muxerWrapper, final StreamEncoderListener listener) {
    	if (listener == null) throw new NullPointerException("StreamEncoderListener is null");
    	if (muxerWrapper == null) throw new NullPointerException("StreamMuxerWrapper is null");
		mMuxerWrapper = muxerWrapper;
		mMuxerWrapper.addEncoder(this);
		mListener = listener;
        synchronized (mSync) {
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = new MediaCodec.BufferInfo();
            // wait for starting thread
            new Thread(this, getClass().getSimpleName()).start();
            try {
            	mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
	}

    public boolean frameAvailableSoon() {
//    	if (DEBUG) Log.v(TAG, "frameAvailableSoon");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false;
            }
            mRequestDrain++;
            mSync.notifyAll();
        }
        return true;
    }

    /**
     * encoding loop on private thread
     */
	@Override
	public void run() {
//		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        synchronized (mSync) {
            mRequestStop = false;
    		mRequestDrain = 0;
            mSync.notify();
        }
        final boolean isRunning = true;
        boolean localRequestStop;
        boolean localRequestDrain;
        while (isRunning) {
        	synchronized (mSync) {
        		localRequestStop = mRequestStop;
        		localRequestDrain = (mRequestDrain > 0);
        		if (localRequestDrain)
        			mRequestDrain--;
        	}
	        if (localRequestStop) {
	           	drain();
	           	// request stop recording
	           	signalEndOfInputStream();
	           	// process output data again for EOS signale
	           	drain();
	           	// release all related objects
	           	release();
	           	break;
	        }
	        if (localRequestDrain) {
	        	drain();
	        } else {
	        	synchronized (mSync) {
		        	try {
						mSync.wait();
					} catch (final InterruptedException e) {
						break;
					}
	        	}
        	}
        } // end of while
		if (DEBUG) Log.d(TAG, "Encoder thread exiting");
        synchronized (mSync) {
        	mRequestStop = true;
            mIsCapturing = false;
        }
	}

	/*
    * preparing method for each sub class
    * this method should be implemented in sub class, so set this as abstract method
    * @throws IOException
    */
   /*package*/ public abstract void prepare() throws IOException;

	/*package*/
	public void startStreaming() {
   	if (DEBUG) Log.v(TAG, "startStreaming");

		// the referent PTS for video and audio encoder.
		mPresentTimeUs = System.nanoTime() / 1000;

		synchronized (mSync) {
			mIsCapturing = true;
			mRequestStop = false;
			mRequestPause = false;
			mSync.notifyAll();
		}
	}

   /**
    * the method to request stop encoding
    */
	/*package*/
   public void stopStreaming() {
		if (DEBUG) Log.v(TAG, "stopStreaming");
		synchronized (mSync) {
			if (!mIsCapturing || mRequestStop) {
				return;
			}
			mRequestStop = true;	// for rejecting newer frame
			mSync.notifyAll();
	        // We can not know when the encoding and writing finish.
	        // so we return immediately after request to avoid delay of caller thread
		}
	}

	/*package*/
	public void pauseStreaming() {
		if (DEBUG) Log.v(TAG, "pauseStreaming");
		synchronized (mSync) {
			if (!mIsCapturing || mRequestStop) {
				return;
			}
			mRequestPause = true;
			mPausetime = System.nanoTime() / 1000;
			mSync.notifyAll();
		}
	}

	/*package*/
	public void resumeRecording() {
		if (DEBUG) Log.v(TAG, "resumeRecording");


		synchronized (mSync) {
			if (!mIsCapturing || mRequestStop) {
				return;
			}
			long resumeTime = (System.nanoTime() / 1000) - mPausetime;
			mPresentTimeUs = mPresentTimeUs + resumeTime;
			mPausetime = 0;
			mRequestPause = false;
			mSync.notifyAll();
		}
	}

//********************************************************************************
//********************************************************************************
    /**
     * Release all released objects
     */
    protected void release() {
		if (DEBUG) Log.d(TAG, "release:");
		try {
			mListener.onStopped(this);
		} catch (final Exception e) {
			Log.e(TAG, "failed onStopped", e);
		}
		mIsCapturing = false;
        if (mMediaCodec != null) {
			try {
	            mMediaCodec.stop();
	            mMediaCodec.release();
	            mMediaCodec = null;
			} catch (final Exception e) {
				Log.e(TAG, "failed releasing MediaCodec", e);
			}
        }
        if (mMuxerStarted) {
       		if (mMuxerWrapper != null) {
       			try {
					mMuxerWrapper.stop();
    			} catch (final Exception e) {
    				Log.e(TAG, "failed stopping muxer", e);
    			}
       		}
        }
        mBufferInfo = null;
    }

    protected void signalEndOfInputStream() {
		if (DEBUG) Log.d(TAG, "sending EOS to encoder");
        // signalEndOfInputStream is only avairable for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
//		mMediaCodec.signalEndOfInputStream();	// API >= 18
        encode(null, 0, getPresentTimeUS());
	}

    /**
     * Method to set byte array to the MediaCodec encoder
     * @param buffer
     * @param length　length of byte array, zero means EOS.
     * @param presentationTimeUs
     */
    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
    	if (!mIsCapturing) return;

        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        while (mIsCapturing) {
	        final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
	        if (inputBufferIndex >= 0) {
	            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	            inputBuffer.clear();
	            if (buffer != null) {
	            	inputBuffer.put(buffer);
	            }
//	            if (DEBUG) Log.v(TAG, "encode:queueInputBuffer");
	            if (length <= 0) {
	            	// send EOS
	            	mIsEOS = true;
	            	if (DEBUG) Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
	            	mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
	            		presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
		            break;
	            } else {
	            	mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
	            		presentationTimeUs, 0);
	            }
	            break;
	        } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
	        	// wait for MediaCodec encoder is ready to encode
	        	// nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
	        	// will wait for maximum TIMEOUT_USEC(10msec) on each call
	        }
        }
    }

    /**
     * drain encoded data and write them to muxer
     */
    protected void drain() {
    	if (mMediaCodec == null) return;
        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        int encoderStatus, count = 0;

        if (mMuxerWrapper == null) {
//        	throw new NullPointerException("muxer is unexpectedly null");
        	Log.w(TAG, "muxer is unexpectedly null");
        	return;
        }
LOOP:	while (mIsCapturing) {
			// get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
                if (!mIsEOS) {
                	if (++count > 5)
                		break LOOP;		// out of while
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            	if (DEBUG) Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                // this should not come when encoding
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();

            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            	if (DEBUG) Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
            	// this status indicate the output format of codec is changed
                // this should come only once before actual encoded data
            	// but this status never come on Android4.3 or less
            	// and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                if (mMuxerStarted) {	// second time request is error
                    throw new RuntimeException("format changed twice");
                }
				// get output format from codec and pass them to muxer
				// getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
               	mTrackIndex = mMuxerWrapper.addTrack(format);
               	mMuxerStarted = true;
               	if (!mMuxerWrapper.start()) {
               		// we should wait until muxer is ready
               		synchronized (mMuxerWrapper) {
	               		while (!mMuxerWrapper.isStarted())
						try {
							mMuxerWrapper.wait(100);
						} catch (final InterruptedException e) {
							break LOOP;
						}
               		}
               	}
            } else if (encoderStatus < 0) {
            	// unexpected status
            	if (DEBUG) Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
            } else {
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                	// this never should come...may be a MediaCodec internal error
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                	// You should set output format to muxer here when you target Android4.3 or less
                	// but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                	// therefor we should expand and prepare output format from buffer data.
                	// This sample is for API>=18(>=Android 4.3), just ignore this flag here
					if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG");
					mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                	// encoded data is ready, clear waiting counter
            		count = 0;
                    if (!mMuxerStarted) {
                    	// muxer is not ready...this will be programing failure.
                        throw new RuntimeException("drain:muxer hasn't started");
                    }
                    // write encoded data to muxer(need to adjust presentationTimeUs.
					if (!mRequestPause) {
	                   	mBufferInfo.presentationTimeUs = getPresentTimeUS();
//						Log.i(TAG, "drainST: "+this.getClass().getName()+": "+encodedData.toString());
	                   	//Todo: send these data buffer
	                   	mMuxerWrapper.writeSampleData(mTrackIndex, encodedData, mBufferInfo);

//						prevOutputPTSUs = mBufferInfo.presentationTimeUs;
					}
                }
                // return buffer to encoder
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                	// when EOS come.
               		mIsCapturing = false;
                    break;      // out of while
                }
            }
        }
    }


	private long mPresentTimeUs=0;
	private long mPausetime;



    protected long getPresentTimeUS() {
		return System.nanoTime() / 1000 - mPresentTimeUs;
    }

}
