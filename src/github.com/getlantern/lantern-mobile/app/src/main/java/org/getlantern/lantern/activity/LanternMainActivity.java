package org.getlantern.lantern.activity;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo; 
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.app.Activity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.MenuItem; 
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getlantern.lantern.config.LanternConfig;
import org.getlantern.lantern.vpn.Service;
import org.getlantern.lantern.model.UI;
import org.getlantern.lantern.sdk.Utils;
import org.getlantern.lantern.R;


public class LanternMainActivity extends Activity implements Handler.Callback {

    private static final String TAG = "LanternMainActivity";
    private static final String PREFS_NAME = "LanternPrefs";
    private static final int CHECK_NEW_VERSION_DELAY = 10000;
 

    private SharedPreferences mPrefs = null;

    private Context context;
    private UI LanternUI;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // the ACTION_SHUTDOWN intent is broadcast when the phone is
        // about to be shutdown. We register a receiver to make sure we
        // clear the preferences and switch the VpnService to the off
        // state when this happens
        IntentFilter filter = new IntentFilter(Intent.ACTION_SHUTDOWN);
        BroadcastReceiver mReceiver = new ShutDownReceiver();
        registerReceiver(mReceiver, filter);

        if (getIntent().getBooleanExtra("EXIT", false)) {
            finish();
            return;
        }

        context = getApplicationContext();

        mPrefs = Utils.getSharedPrefs(context);

        setContentView(R.layout.activity_lantern_main);

        // setup our UI
        try { 
            LanternUI = new UI(this, mPrefs);
            LanternUI.setupSideMenu();
            LanternUI.setupStatusToast();
            // configure actions to be taken whenever slider changes state
            LanternUI.setupLanternSwitch();
            PromptVpnActivity.LanternUI = LanternUI;
            Service.LanternUI = LanternUI;
        } catch (Exception e) {
            Log.d(TAG, "Got an exception " + e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // we check if mPrefs has been initialized before
        // since onCreate and onResume are always both called
        if (mPrefs != null) {
            LanternUI.setBtnStatus();
        }
    }

    @Override
    protected void onDestroy() {
        try {

            Utils.clearPreferences(this);
            stopLantern();
            // give Lantern a second to stop
            Thread.sleep(1000);
        } catch (Exception e) {

        }
        super.onDestroy();
    }

    // quitLantern is the side menu option and cleanyl exits the app
    public void quitLantern() {
        try {
            Log.d(TAG, "About to exit Lantern...");

            stopLantern();
            Utils.clearPreferences(this);

            // sleep for a few ms before exiting
            Thread.sleep(200);

            finish();
            moveTaskToBack(true);

        } catch (Exception e) {
            Log.e(TAG, "Got an exception when quitting Lantern " + e.getMessage());
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            //Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public void sendDesktopVersion(View view) {
        LanternUI.sendDesktopVersion(view);
    }

    // isNetworkAvailable checks whether or not we are connected to
    // the Internet; if no connection is available, the toggle
    // switch is inactive
    public boolean isNetworkAvailable() {
        final Context context = this;
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    // Prompt the user to enable full-device VPN mode
    public void enableVPN() {
        Log.d(TAG, "Load VPN configuration");

        Thread thread = new Thread() {
            public void run() { 
                Intent intent = new Intent(LanternMainActivity.this, PromptVpnActivity.class);
                if (intent != null) {
                    startActivity(intent);
                }
            }
        };
        thread.start();
    }

    public void stopLantern() {
        Log.d(TAG, "Stopping Lantern...");
        try {
            Thread thread = new Thread() {
                public void run() { 

                    Intent service = new Intent(LanternMainActivity.this, Service.class);
                    if (service != null) {
                        service.setAction(LanternConfig.DISABLE_VPN);
                        startService(service);
                    }
                }
            };
            thread.start();
        } catch (Exception e) {
            Log.d(TAG, "Got an exception trying to stop Lantern: " + e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle
        // If it returns true, then it has handled
        // the nav drawer indicator touch event
        if (LanternUI.optionSelected(item)) {
            return true;
        }

        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        LanternUI.syncState();
    }

    private class ShutDownReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Utils.clearPreferences(context);
        }
    }

    public void checkNewVersion() {
        try {
            final Context context = this;

            String latestVersion = "test";

            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            String version = pInfo.versionName;
            Log.d(TAG, "Current version of app is " + version);

            if (latestVersion != null && !latestVersion.equals(version)) {
                // Latest version of FireTweet and the version currently running differ
                // display the update view
                final Intent intent = new Intent(context, UpdaterActivity.class);
                context.startActivity(intent);
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error fetching package information");
        }
    }  
}
