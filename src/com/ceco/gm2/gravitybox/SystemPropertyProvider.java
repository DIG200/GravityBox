/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.gm2.gravitybox;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SystemPropertyProvider {
    private static final String TAG = "GB:SystemPropertyProvider";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final boolean DEBUG = false;

    public static final String ACTION_GET_SYSTEM_PROPERTIES = 
            "gravitybox.intent.action.ACTION_GET_SYSTEM_PROPERTIES";
    public static final int RESULT_SYSTEM_PROPERTIES = 1025;
    public static final String ACTION_REGISTER_UUID = 
            "gravitybox.intent.action.ACTION_REGISTER_UUID";
    public static final String EXTRA_UUID = "uuid";
    private static final String SETTING_GRAVITYBOX_UUID = "gravitybox_uuid";
    private static final String SETTING_UNC_TRIAL_COUNTDOWN = "gravitybox_unc_trial_countdown";

    private static String mSettingsUuid;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static boolean getSystemConfigBool(Resources res, String name) {
        final int resId = res.getIdentifier(name, "bool", "android");
        return (resId == 0 ? false : res.getBoolean(resId));
    }

    public static int getSystemConfigInteger(Resources res, String name) {
        final int resId = res.getIdentifier(name, "integer", "android");
        return (resId == 0 ? -1 : res.getInteger(resId));
    }

    public static void init(final ClassLoader classLoader) {
        try {
            final Class<?> classSystemUIService = XposedHelpers.findClass(
                    "com.android.systemui.SystemUIService", classLoader);
            XposedHelpers.findAndHookMethod(classSystemUIService, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    Context context = (Context) param.thisObject;
                    if (context != null) {
                        if (DEBUG) log("SystemUIService created. Registering BroadcastReceiver");
                        final ContentResolver cr = context.getContentResolver();

                        // register or decrease UNC trial countdown
                        int uncTrialCountdown = Settings.System.getInt(cr, SETTING_UNC_TRIAL_COUNTDOWN, -1);
                        if (uncTrialCountdown == -1) {
                            Settings.System.putInt(cr, SETTING_UNC_TRIAL_COUNTDOWN, 20);
                        } else {
                            if (--uncTrialCountdown >= 0) {
                                Settings.System.putInt(cr, SETTING_UNC_TRIAL_COUNTDOWN, uncTrialCountdown);
                            }
                        }

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(ACTION_GET_SYSTEM_PROPERTIES);
                        intentFilter.addAction(ACTION_REGISTER_UUID);
                        context.registerReceiver(new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                if (DEBUG) log("Broadcast received: " + intent.toString());
                                if (intent.getAction().equals(ACTION_GET_SYSTEM_PROPERTIES)
                                        && intent.hasExtra("receiver")) {
                                    mSettingsUuid = intent.getStringExtra("settings_uuid");
                                    final Resources res = context.getResources();
                                    ResultReceiver receiver = intent.getParcelableExtra("receiver");
                                    Bundle data = new Bundle();
                                    data.putBoolean("hasGeminiSupport", Utils.hasGeminiSupport());
                                    data.putBoolean("isTablet", Utils.isTablet());
                                    data.putBoolean("hasNavigationBar",
                                            getSystemConfigBool(res, "config_showNavigationBar"));
                                    data.putBoolean("unplugTurnsOnScreen", 
                                            getSystemConfigBool(res, "config_unplugTurnsOnScreen"));
                                    data.putInt("defaultNotificationLedOff",
                                            getSystemConfigInteger(res, "config_defaultNotificationLedOff"));
                                    data.putBoolean("uuidRegistered", (mSettingsUuid != null &&
                                            mSettingsUuid.equals(Settings.System.getString(
                                                    cr, SETTING_GRAVITYBOX_UUID))));
                                    data.putInt("uncTrialCountdown", Settings.System.getInt(cr,
                                            SETTING_UNC_TRIAL_COUNTDOWN, 20));
                                    data.putBoolean("isAcerShellEnabled",
                                            Utils.SystemProp.get("acer.shell.mode", "none").equalsIgnoreCase("enabled"));
                                    data.putBoolean("isAcerLaunchpadEnabled",
                                            Utils.SystemProp.getBoolean("ENABLE_LAUNCHPAD", false));
                                    if (DEBUG) {
                                        log("hasGeminiSupport: " + data.getBoolean("hasGeminiSupport"));
                                        log("isTablet: " + data.getBoolean("isTablet"));
                                        log("hasNavigationBar: " + data.getBoolean("hasNavigationBar"));
                                        log("unplugTurnsOnScreen: " + data.getBoolean("unplugTurnsOnScreen"));
                                        log("defaultNotificationLedOff: " + data.getInt("defaultNotificationLedOff"));
                                        log("uuidRegistered: " + data.getBoolean("uuidRegistered"));
                                        log("uncTrialCountdown: " + data.getInt("uncTrialCountdown"));
                                        log("isAcerShellEnabled: " + data.getInt("isAcerShellEnabled"));
                                        log("isAcerLaunchpadEnabled: " + data.getInt("isAcerLaunchpadEnabled"));
                                    }
                                    receiver.send(RESULT_SYSTEM_PROPERTIES, data);
                                } else if (intent.getAction().equals(ACTION_REGISTER_UUID) && 
                                            intent.hasExtra(EXTRA_UUID) && 
                                            intent.getStringExtra(EXTRA_UUID).equals(mSettingsUuid)) {
                                    Settings.System.putString(cr, SETTING_GRAVITYBOX_UUID, mSettingsUuid);
                                }
                            }
                        }, intentFilter);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
