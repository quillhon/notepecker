/*==============================================================================
            Copyright (c) 2012 QUALCOMM Austria Research Center GmbH.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary
==============================================================================*/

package ws.quill.notepecker.utils;

import android.util.Log;

/**
 * DebugLog is a support class for the QCAR samples applications. Exposes
 * functionality for logging.
 *
 * */

public class DebugLog
{
    private static final String LOGTAG = "QCAR";


    /** Logging functions to generate ADB logcat messages. */

    public static void LOGE(String nMessage)
    {
        Log.e(LOGTAG, nMessage);
    }


    public static void LOGW(String nMessage)
    {
        Log.w(LOGTAG, nMessage);
    }


    public static void LOGD(String nMessage)
    {
        Log.d(LOGTAG, nMessage);
    }


    public static void LOGI(String nMessage)
    {
        Log.i(LOGTAG, nMessage);
    }
}
