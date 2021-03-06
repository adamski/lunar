/**
 * Base C Source code (functions.c)
 * Created: Fri 04 Jan 2013 02:26:39 PM CET
 *
 * This C source code was developped by François-Xavier Thomas.
 * You are free to copy, adapt or modify it.
 * If you do so, however, leave my name somewhere in the credits, I'd appreciate it ;)
 *
 * @author François-Xavier Thomas <fx.thomas@gmail.com>
 * @version 1.0
 */

#include <aubio/aubio.h>
#include <android/log.h>
#include "native.h"

JNIEXPORT void JNICALL Java_com_github_fxthomas_lunar_Song_00024_computeBPM
  (JNIEnv *jenv, jobject jself, jbyteArray jaudiodata) {
    __android_log_print (ANDROID_LOG_INFO, "Aubio", "Computing BPM");

    // Read input data
    jbyte *audiodata = jenv->GetByteArrayElements (jaudiodata, NULL);
    jsize audiolength = jenv->GetArrayLength (jaudiodata);
    __android_log_print (ANDROID_LOG_INFO, "Aubio", "Got byte[%d] from Java", audiolength);

    //fvec_t *ibuf = new_fvec ();

    // Release input data
    jenv->ReleaseByteArrayElements(jaudiodata, audiodata, JNI_ABORT);

    __android_log_print (ANDROID_LOG_INFO, "Aubio", "BPM computed!");
}
