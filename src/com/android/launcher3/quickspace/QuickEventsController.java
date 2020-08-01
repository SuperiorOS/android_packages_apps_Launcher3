/*
 * Copyright (C) 2020 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.quickspace;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

public class QuickEventsController {

    // private static final int AMBIENT_INFO_MAX_DURATION = 120000; // 2 minutes
    private static final String SETTING_DEVICE_INTRO_COMPLETED = "device_introduction_completed";
    private Context mContext;

    private String mEventTitle;
    private String mEventTitleSub;
    private OnClickListener mEventTitleSubAction = null;
    private int mEventSubIcon;

    private boolean mIsQuickEvent = false;
    private boolean mRunning;

    // Device Intro
    private boolean mEventIntro = false;
    private boolean mIsFirstTimeDone = false;
    private SharedPreferences mPreferences;

     /** Ambient Play
    private boolean mEventAmbientPlay = false;
    private long mLastAmbientInfo;
    private BroadcastReceiver mAmbientReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(AmbientPlayHistoryManager.INTENT_SONG_MATCH.getAction())) {
                initAmbientPlayEvent();
            }
        }
    }; **/

    public QuickEventsController(Context context) {
        mContext = context;
        initQuickEvents();
    }

    public void initQuickEvents() {
        mPreferences = mContext.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        mIsFirstTimeDone = mPreferences.getBoolean(SETTING_DEVICE_INTRO_COMPLETED, false);
        updateQuickEvents();
    }

    public void updateQuickEvents() {
        deviceIntroEvent();
        //ambientPlayEvent();
    }

    private void deviceIntroEvent() {
        if (!mRunning) return;

        if (mIsFirstTimeDone) {
            mEventIntro = false;
            return;
        }
        mIsQuickEvent = true;
        mEventIntro = true;
        mEventTitle = mContext.getResources().getString(R.string.quick_event_rom_intro_welcome);
        mEventTitleSub = mContext.getResources().getStringArray(R.array.welcome_message_variants)[getLuckyNumber(0,6)];

        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                mContext.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(SETTING_DEVICE_INTRO_COMPLETED, true)
                        .commit();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    Launcher.getLauncher(mContext).startActivitySafely(view, intent, null, null);
                } catch (ActivityNotFoundException ex) {
                }
                mIsQuickEvent = false;
            }
        };
    }

    /**public void ambientPlayEvent() {
        if (mEventAmbientPlay) {
            boolean infoExpired = System.currentTimeMillis() - mLastAmbientInfo > AMBIENT_INFO_MAX_DURATION;
            if (infoExpired) {
                mIsQuickEvent = false;
                mEventAmbientPlay = false;
            }
        }
    }

    public void initAmbientPlayEvent() {
        if (mEventIntro) return;
        List<AmbientPlayHistoryEntry> songInfo = AmbientPlayHistoryManager.getSongs(mContext);
        if (songInfo.size() < 1) return;
        AmbientPlayHistoryEntry entry = songInfo.get(0);
        mEventTitle = mContext.getResources().getString(R.string.quick_event_ambient_now_playing);
        mEventTitleSub = String.format(mContext.getResources().getString(
                R.string.quick_event_ambient_song_artist), entry.getSongTitle(), entry.getArtistTitle());
        mEventSubIcon = R.drawable.ic_music_note_24dp;
        mIsQuickEvent = true;
        mEventAmbientPlay = true;
        mLastAmbientInfo = System.currentTimeMillis();

        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                String query = String.format(mContext.getResources().getString(
                        R.string.quick_event_ambient_song_artist), entry.getSongTitle(), entry.getArtistTitle());
                final Intent ambient = new Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, query);
                try {
                    Launcher.getLauncher(mContext).startActivitySafely(view, ambient, null);
                } catch (ActivityNotFoundException ex) {
                }
            }
        };
    } **/

    public boolean isQuickEvent() {
        return mIsQuickEvent;
    }

    public String getTitle() {
        return mEventTitle;
    }

    public String getActionTitle() {
        return mEventTitleSub;
    }

    public OnClickListener getAction() {
        return mEventTitleSubAction;
    }

    public int getActionIcon() {
        return mEventSubIcon;
    }

    public int getLuckyNumber(int max) {
        return getLuckyNumber(0, max);
    }

    public int getLuckyNumber(int min, int max) {
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    public void onPause() {
        mRunning = false;
    }

    public void onResume() {
        mRunning = true;
    }
}
