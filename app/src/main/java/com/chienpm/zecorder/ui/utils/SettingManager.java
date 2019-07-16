package com.chienpm.zecorder.ui.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.chienpm.zecorder.R;

public class SettingManager {

    public static int getCountdown(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_common_countdown);
        String defValue = getStringRes(context, R.string.default_setting_countdown);
        String res = preferences.getString(key, defValue);
        return Integer.parseInt(res);
    }

    private static String getOrientation(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_common_orientation);
        String defValue = getStringRes(context, R.string.default_setting_orientation);

        return preferences.getString(key, defValue);
    }

    private static int getVideoBitrate(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_video_bitrate);
        String defValue = getStringRes(context, R.string.default_setting_bitrate);
        String res = preferences.getString(key, defValue);
        return Integer.parseInt(res);
    }

    private static String getVideoResolution(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_video_resolution);
        String defValue = getStringRes(context, R.string.default_setting_resolution);

        return preferences.getString(key, defValue);
    }

    private static int getVideoFPS(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_video_fps);
        String defValue = getStringRes(context, R.string.default_setting_fps);
        String res = preferences.getString(key, defValue);
        return Integer.parseInt(res);
    }

    private static String getCameraMode(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_camera_mode);
        String defValue = getStringRes(context, R.string.default_camera_mode);
        String res = preferences.getString(key, defValue);
        return res;
    }

    private static String getCameraPosition(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_camera_position);
        String defValue = getStringRes(context, R.string.default_camera_position);
        String res = preferences.getString(key, defValue);
        return res;
    }

    private static String getCameraSize(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getStringRes(context, R.string.setting_camera_size);
        String defValue = getStringRes(context, R.string.default_camera_size);
        String res = preferences.getString(key, defValue);
        return res;
    }


    @NonNull
    private static String getStringRes(Context context, int resId) {
        return context.getResources().getString(resId);
    }

    public static VideoProfile getVideoProfile(Context context) {
        VideoProfile videoProfile = null;

        String resolution = getVideoResolution(context);
        String orientation = getOrientation(context);

        switch (resolution){
            case "SD": videoProfile = VideoProfile.VIDEO_PROFILE_SD; break;
            case "HD": videoProfile = VideoProfile.VIDEO_PROFILE_HD; break;
            case "FHD": videoProfile =  VideoProfile.VIDEO_PROFILE_FHD; break;
        }

        int fps = getVideoFPS(context);

        int bitrate = getVideoBitrate(context);

        if(bitrate != -1) // not auto
            videoProfile.setBitrate(bitrate);

        if(orientation.equals("Portrait")){ //DEFAULT IS LANDSCAPE
            videoProfile.setOrientation(VideoProfile.ORIENTATION_PORTRAIT);
        }

        videoProfile.setFPS(fps);
        Log.d("chienpm", "getVideoProfile: "+videoProfile.toString());
        return videoProfile;
    }

    public static MyCameraProfile getCameraProfile(Context context) {
        String size = getCameraSize(context);
        String mode = getCameraMode(context);
        String pos = getCameraPosition(context);
        return new MyCameraProfile(mode, pos, size);
    }
}
