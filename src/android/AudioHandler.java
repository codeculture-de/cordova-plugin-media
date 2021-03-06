/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.media;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.google.android.vending.expansion.downloader.FakeR;
import com.google.android.vending.expansion.downloader.Helpers;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.HashMap;

/**
 * This class called by CordovaActivity to play and record audio.
 * The file can be local or over a network using http.
 *
 * Audio formats supported (tested):
 * 	.mp3, .wav
 *
 * Local audio files must reside in one of two places:
 * 		android_asset: 		file name must start with /android_asset/sound.mp3
 * 		sdcard:				file name is just sound.mp3
 */
public class AudioHandler extends CordovaPlugin {

    public static String TAG = "AudioHandler";
    HashMap<String, AudioPlayer> players;	// Audio player object
    ArrayList<AudioPlayer> pausedForPhone;     // Audio players that were paused when phone call came in

    static int mainVersion = 1;
    static int patchVersion = 1;
    static long fileSize = 1;
    public static final int REQUEST_CODE = 234256412;

    /**
     * Constructor.
     */
    public AudioHandler() {
        this.players = new HashMap<String, AudioPlayer>();
        this.pausedForPhone = new ArrayList<AudioPlayer>();
    }

    /**
     * Executes the request and returns PluginResult.
     * @param action 		The action to execute.
     * @param args 			JSONArry of arguments for the plugin.
     * @param callbackContext		The callback context used when calling back into JavaScript.
     * @return 				A PluginResult object with a status and message.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        CordovaResourceApi resourceApi = webView.getResourceApi();
        PluginResult.Status status = PluginResult.Status.OK;
        String result = "";

        //Init FakeR Helper is its still not Initialized
        Context ctx = cordova.getActivity().getApplicationContext();
        if(Helpers.fakeR == null) {
            Helpers.fakeR = new FakeR(ctx);
        }

        if (action.equals("startRecordingAudio")) {
            String target = args.getString(1);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            this.startRecordingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr));
        }
        else if (action.equals("stopRecordingAudio")) {
            this.stopRecordingAudio(args.getString(0));
        }
        else if (action.equals("startPlayingAudio")) {
            String target = args.getString(1);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            this.startPlayingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr));
        }
        else if (action.equals("seekToAudio")) {
            this.seekToAudio(args.getString(0), args.getInt(1));
        }
        else if (action.equals("pausePlayingAudio")) {
            this.pausePlayingAudio(args.getString(0));
        }
        else if (action.equals("stopPlayingAudio")) {
            this.stopPlayingAudio(args.getString(0));
        } else if (action.equals("setVolume")) {
           try {
               this.setVolume(args.getString(0), Float.parseFloat(args.getString(1)));
           } catch (NumberFormatException nfe) {
               //no-op
           }
        } else if (action.equals("getCurrentPositionAudio")) {
            float f = this.getCurrentPositionAudio(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(status, f));
            return true;
        }
        else if (action.equals("getDurationAudio")) {
            float f = this.getDurationAudio(args.getString(0), args.getString(1));
            callbackContext.sendPluginResult(new PluginResult(status, f));
            return true;
        }
        else if (action.equals("create")) {
            String id = args.getString(0);
            String src = FileHelper.stripFileProtocol(args.getString(1));
            AudioPlayer audio = new AudioPlayer(this, id, src);
            this.players.put(id, audio);
        }
        else if (action.equals("release")) {
            boolean b = this.release(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(status, b));
            return true;
        }
        else if (action.equals("assetCheckDownloader")) {
            callbackContext.sendPluginResult(executeAssetCheck(args));
            return true;
        }
        else if (action.equals("assetCheckRequired")) {
            callbackContext.sendPluginResult(executeAssetCheckRequired(args));
            return true;
        }
        else { // Unrecognized action.
            return false;
        }

        callbackContext.sendPluginResult(new PluginResult(status, result));

        return true;
    }

    private PluginResult executeAssetCheck(JSONArray data) {
        try {
            MediaDownloaderService.BASE64_PUBLIC_KEY = data.getString(0);
            AudioHandler.mainVersion = AudioHandler.patchVersion = data.getInt(1);
            AudioHandler.fileSize = data.getLong(2);

            xAPKS = new XAPKFile[] {
                    new XAPKFile(true, AudioHandler.mainVersion, AudioHandler.fileSize)
            };

            Intent intent = new Intent("org.apache.cordova.media.AudioHandler.VIEW");
            intent.addCategory(Intent.CATEGORY_DEFAULT);

            Log.d(TAG, "Starting intend " + intent);
            Log.d(TAG, "Key is: " + MediaDownloaderService.BASE64_PUBLIC_KEY);
            Log.d(TAG, "Trying to get : main." + AudioHandler.mainVersion + cordova.getActivity().getApplicationContext().getPackageName() + ".obb" );

            this.cordova.startActivityForResult((CordovaPlugin) this, intent, REQUEST_CODE);

            return new PluginResult(PluginResult.Status.OK);
        } catch (Exception e) {
            return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        }
    }

    private PluginResult executeAssetCheckRequired(JSONArray data) {


        ZipResourceFile zipResourceFile = null;
        try {
            AudioHandler.mainVersion = AudioHandler.patchVersion = data.getInt(0);
            zipResourceFile = AudioPlayer.getZipResourceFile(getContext());

            if(zipResourceFile == null) {
                return new PluginResult(PluginResult.Status.OK, true);
            }

            return new PluginResult(PluginResult.Status.OK, false);
        } catch (Exception e) {
            return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE) {
            Log.d(TAG, String.valueOf(resultCode));
        }
    }

     /**
     * Stop all audio players and recorders.
     */
    public void onDestroy() {
        for (AudioPlayer audio : this.players.values()) {
            audio.destroy();
        }
        this.players.clear();
    }

    /**
     * Stop all audio players and recorders on navigate.
     */
    @Override
    public void onReset() {
        onDestroy();
    }

    /**
     * Called when a message is sent to plugin.
     *
     * @param id            The message id
     * @param data          The message data
     * @return              Object to stop propagation or null
     */
    public Object onMessage(String id, Object data) {

        // If phone message
        if (id.equals("telephone")) {

            // If phone ringing, then pause playing
            if ("ringing".equals(data) || "offhook".equals(data)) {

                // Get all audio players and pause them
                for (AudioPlayer audio : this.players.values()) {
                    if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        this.pausedForPhone.add(audio);
                        audio.pausePlaying();
                    }
                }

            }

            // If phone idle, then resume playing those players we paused
            else if ("idle".equals(data)) {
                for (AudioPlayer audio : this.pausedForPhone) {
                    audio.startPlaying(null);
                }
                this.pausedForPhone.clear();
            }
        }
        return null;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Release the audio player instance to save memory.
     * @param id				The id of the audio player
     */
    private boolean release(String id) {
        if (!this.players.containsKey(id)) {
            return false;
        }
        AudioPlayer audio = this.players.get(id);
        this.players.remove(id);
        audio.destroy();
        return true;
    }

    /**
     * Start recording and save the specified file.
     * @param id				The id of the audio player
     * @param file				The name of the file
     */
    public void startRecordingAudio(String id, String file) {
        AudioPlayer audio = this.players.get(id);
        if ( audio == null) {
            audio = new AudioPlayer(this, id, file);
            this.players.put(id, audio);
        }
        audio.startRecording(file);
    }

    /**
     * Stop recording and save to the file specified when recording started.
     * @param id				The id of the audio player
     */
    public void stopRecordingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopRecording();
        }
    }

    /**
     * Start or resume playing audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     */
    public void startPlayingAudio(String id, String file) {
        AudioPlayer audio = this.players.get(id);
        if (audio == null) {
            audio = new AudioPlayer(this, id, file);
            this.players.put(id, audio);
        }
        audio.startPlaying(file);
    }

    /**
     * Seek to a location.
     * @param id				The id of the audio player
     * @param milliseconds		int: number of milliseconds to skip 1000 = 1 second
     */
    public void seekToAudio(String id, int milliseconds) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.seekToPlaying(milliseconds);
        }
    }

    /**
     * Pause playing.
     * @param id				The id of the audio player
     */
    public void pausePlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.pausePlaying();
        }
    }

    /**
     * Stop playing the audio file.
     * @param id				The id of the audio player
     */
    public void stopPlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopPlaying();
            //audio.destroy();
            //this.players.remove(id);
        }
    }

    /**
     * Get current position of playback.
     * @param id				The id of the audio player
     * @return 					position in msec
     */
    public float getCurrentPositionAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            return (audio.getCurrentPosition() / 1000.0f);
        }
        return -1;
    }

    /**
     * Get the duration of the audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     * @return					The duration in msec.
     */
    public float getDurationAudio(String id, String file) {

        // Get audio file
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            return (audio.getDuration(file));
        }

        // If not already open, then open the file
        else {
            audio = new AudioPlayer(this, id, file);
            this.players.put(id, audio);
            return (audio.getDuration(file));
        }
    }

    /**
     * Set the audio device to be used for playback.
     *
     * @param output			1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public void setAudioOutputDevice(int output) {
        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (output == 2) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
        }
        else if (output == 1) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
        }
        else {
            System.out.println("AudioHandler.setAudioOutputDevice() Error: Unknown output device.");
        }
    }

    /**
     * Get the audio device to be used for playback.
     *
     * @return					1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public int getAudioOutputDevice() {
        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (audiMgr.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_EARPIECE) {
            return 1;
        }
        else if (audiMgr.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_SPEAKER) {
            return 2;
        }
        else {
            return -1;
        }
    }

    /**
     * Set the volume for an audio device
     *
     * @param id				The id of the audio player
     * @param volume            Volume to adjust to 0.0f - 1.0f
     */
    public void setVolume(String id, float volume) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.setVolume(volume);
        } else {
            System.out.println("AudioHandler.setVolume() Error: Unknown Audio Player " + id);
        }
    }

    public Context getContext() {
        return cordova.getActivity().getApplicationContext();
    }

    /**
     * This is a little helper class that demonstrates simple testing of an
     * Expansion APK file delivered by Market. You may not wish to hard-code
     * things such as file lengths into your executable... and you may wish to
     * turn this code off during application development.
     */
    public static class XAPKFile {
        public final boolean mIsMain;
        public final int mFileVersion;
        public final long mFileSize;

        XAPKFile(boolean isMain, int fileVersion, long fileSize) {
            mIsMain = isMain;
            mFileVersion = fileVersion;
            mFileSize = fileSize;
        }
    }

    /**
     * Here is where you place the data that the validator will use to determine
     * if the file was delivered correctly. This is encoded in the source code
     * so the application can easily determine whether the file has been
     * properly delivered without having to talk to the server. If the
     * application is using LVL for licensing, it may make sense to eliminate
     * these checks and to just rely on the server.
     */
    public static XAPKFile[] xAPKS = null;
}
