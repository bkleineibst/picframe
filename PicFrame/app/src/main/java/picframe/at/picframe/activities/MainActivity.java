/*
   Copyright (C) 2015 Myra Fuchs, Linda Spindler, Clemens Hlawacek, Ebenezer Bonney Ussher,
   Martin Bayerl, Christoph Krasa

   This file is part of PicFrame.

   PicFrame is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PicFrame is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with PicFrame.  If not, see <http://www.gnu.org/licenses/>.
*/

package picframe.at.picframe.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.PageTransformer;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.app.Dialog;
import android.widget.EditText;
import android.text.InputType;
import android.app.ActivityManager;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import picframe.at.picframe.R;
import picframe.at.picframe.helper.GlobalPhoneFuncs;
import picframe.at.picframe.helper.Keys;
import picframe.at.picframe.helper.alarm.AlarmScheduler;
import picframe.at.picframe.helper.PhotoDateCache;
import picframe.at.picframe.helper.viewpager.AccordionTransformer;
import picframe.at.picframe.helper.viewpager.BackgroundToForegroundTransformer;
import picframe.at.picframe.helper.viewpager.CubeOutTransformer;
import picframe.at.picframe.helper.viewpager.CustomViewPager;
import picframe.at.picframe.helper.viewpager.DrawFromBackTransformer;
import picframe.at.picframe.helper.viewpager.EXIF_helper;
import picframe.at.picframe.helper.viewpager.FadeInFadeOutTransformer;
import picframe.at.picframe.helper.viewpager.FlipVerticalTransformer;
import picframe.at.picframe.helper.viewpager.ForegroundToBackgroundTransformer;
import picframe.at.picframe.helper.viewpager.Gestures;
import picframe.at.picframe.helper.viewpager.RotateDownTransformer;
import picframe.at.picframe.helper.viewpager.SlideshowHistory;
import picframe.at.picframe.helper.viewpager.StackTransformer;
import picframe.at.picframe.helper.viewpager.ZoomInTransformer;
import picframe.at.picframe.helper.viewpager.ZoomOutPageTransformer;
import picframe.at.picframe.service.DownloadService;
import picframe.at.picframe.settings.AppData;

public class MainActivity extends ActionBarActivity {
    /**
     * Append a message to the debug log overlay and scroll to bottom.
     */
    private void appendDebugLog(String message) {
        if (debugLogTextView != null && debugLogScrollView != null) {
            debugLogTextView.append(message + "\n");
            debugLogScrollView.post(new Runnable() {
                @Override
                public void run() {
                    debugLogScrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

	private static final int REQUEST_SELECT_PHOTO = 2001;

    private final static boolean DEBUG = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 1001;
    private ResponseReceiver receiver;
    LocalBroadcastManager broadcastManager;

    private ImagePagerAdapter imagePagerAdapter;
    private CustomViewPager pager;
    private final long countdownIntervalInMilliseconds = 2*60*1000-50; // interval to slightly less than 2 minutes
    private CountDownTimer countDownTimer;

    private static Context mContext;
    private String mOldPath;
    private boolean mOldRecursive;
    private RelativeLayout mainLayout;
    private boolean paused;
    private long remainingDisplayTime = 0; // in seconds

    private static final int nbOfExamplePictures = 6;
    private static boolean showExamplePictures = false;

    @SuppressWarnings("unused")
    public static ProgressBar mProgressBar;                             //TODO still needed?
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static Animation mFadeInAnim, mFadeOutAnim;                 //TODO still needed?

    private ArrayList<PageTransformer> transformers;
    private List<String> mFilePaths;
    private int size;
    private int currentPage;
    private boolean rightToLeft;
    private Handler actionbarHideHandler;
    private ImageView mPause;
    private LinearLayout mRemainingTimeLayout;
    private AlertDialog click_on_settings_dialog;
    private Dialog syncProgressDialog;
    private View syncProgressBarView;
    private ArrayList<String> syncErrors = new ArrayList<>();

    // Debug log overlay UI
    private RelativeLayout debugLogOverlay;
    private TextView debugLogTextView;
    private ScrollView debugLogScrollView;
    /**
     * Show the sync progress overlay.
     * @param message The main message (e.g., "Syncing...")
     * @param fileName The file currently being synced (optional)
     * @param percent The percent complete (0-100, or -1 for indeterminate)
     */
    private void showSyncProgress(String message, String fileName, int percent) {
        if (syncProgressBarView != null) {
            if (percent >= 100) {
                // Hide the progress bar when sync is complete
                syncProgressBarView.setVisibility(View.GONE);
                return;
            }
            TextView text = syncProgressBarView.findViewById(R.id.syncProgressText);
            TextView fileText = syncProgressBarView.findViewById(R.id.syncProgressFile);
            ProgressBar bar = syncProgressBarView.findViewById(R.id.syncProgressSpinner);
            final android.widget.ImageButton btnToggleDebugLog = syncProgressBarView.findViewById(R.id.btnToggleDebugLog);
            if (btnToggleDebugLog != null) {
                btnToggleDebugLog.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (debugLogOverlay != null) {
                            if (debugLogOverlay.getVisibility() == View.VISIBLE) {
                                debugLogOverlay.setVisibility(View.GONE);
                                btnToggleDebugLog.setImageResource(android.R.drawable.ic_menu_info_details);
                            } else {
                                debugLogOverlay.setVisibility(View.VISIBLE);
                                btnToggleDebugLog.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                            }
                        }
                    }
                });
                // Set initial icon based on overlay visibility
                if (debugLogOverlay != null && debugLogOverlay.getVisibility() == View.VISIBLE) {
                    btnToggleDebugLog.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                } else {
                    btnToggleDebugLog.setImageResource(android.R.drawable.ic_menu_info_details);
                }
            }
            if (text != null) {
                if (percent >= 0 && percent < 100) {
                    text.setText((message != null ? message : getString(R.string.syncing)) + " (" + percent + "%)");
                } else {
                    text.setText(message != null ? message : getString(R.string.syncing));
                }
            }
            if (fileText != null) fileText.setText(fileName != null ? fileName : "");
            if (bar != null) {
                if (percent >= 0) {
                    bar.setIndeterminate(false);
                    bar.setMax(100);
                    bar.setProgress(percent);
                } else {
                    bar.setIndeterminate(true);
                }
            }
            syncProgressBarView.setVisibility(View.VISIBLE);
        }
        appendDebugLog(String.format("Sync progress: %s (%d%%)", fileName != null ? fileName : "", percent));
    }

    /**
     * Update the sync progress overlay with the current file and percent.
     * Call this from your sync logic as files are processed.
     */
    public void updateSyncProgress(String fileName, int percent) {
        showSyncProgress(getString(R.string.syncing), fileName, percent);
        // Also ensure the progress bar is hidden if percent >= 100
        if (percent >= 100) {
            hideSyncProgress();
        }
        appendDebugLog(String.format("updateSyncProgress: %s (%d%%)", fileName != null ? fileName : "", percent));
    }

    /**
     * Hide the sync progress overlay.
     */
    private void hideSyncProgress() {
        if (syncProgressBarView != null) {
            syncProgressBarView.setVisibility(View.GONE);
        }
        appendDebugLog("Sync progress hidden.");
    }

    //public static boolean mConnCheckOC, mConnCheckSMB; //TODO still needed?
    public boolean mDoubleBackToExitPressedOnce;
    
    // Kiosk mode: tracks if user has authenticated to exit
    private boolean kioskExitAuthorized = false;
    // Kiosk mode: Handler for automatic re-pinning
    private Handler kioskRepinHandler;
    private Runnable kioskRepinRunnable;
    private static final long KIOSK_REPIN_DELAY = 10000; // Re-pin after 10 seconds
    
    // Slideshow history for random mode navigation
    private SlideshowHistory slideshowHistory;
    private int lastRecordedPage = -1;
    private boolean navigatingFromHistory = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check for MANAGE_EXTERNAL_STORAGE permission on Android 11+
        checkStoragePermission();

        //mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mainLayout = (RelativeLayout) findViewById(R.id.mainLayout);
        mPause = (ImageView) findViewById(R.id.pauseIcon);
        mRemainingTimeLayout = (LinearLayout) findViewById(R.id.remaining_time);
        mFadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        mFadeOutAnim = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        //mConnCheckOC = false;
        //mConnCheckSMB = false;
        rightToLeft = true;
        paused = false;
        enableGestures();
        //deletePreferences();
        //createSettingsIfInexistent();
        if(getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        initializeTransitions();
        pager = (CustomViewPager) findViewById(R.id.pager);
        mContext = getApplicationContext();
        loadAdapter();
        setUpSlideShow();

        mOldPath = AppData.getImagePath();
        mOldRecursive = AppData.getRecursiveSearch();

        // Inflate and add the sync progress bar overlay (non-blocking, top of screen)
        LayoutInflater inflater = LayoutInflater.from(this);
        syncProgressBarView = inflater.inflate(R.layout.sync_progress_bar, mainLayout, false);
        mainLayout.addView(syncProgressBarView);
        syncProgressBarView.setVisibility(View.GONE);

        // Create and add the debug log overlay (bottom of screen)
        debugLogOverlay = new RelativeLayout(this);
        debugLogOverlay.setBackgroundColor(0xAA000000); // semi-transparent black
        RelativeLayout.LayoutParams overlayParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            getResources().getDisplayMetrics().heightPixels / 2 // 1/2 screen height
        );
        overlayParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        debugLogOverlay.setLayoutParams(overlayParams);

        debugLogScrollView = new ScrollView(this);
        RelativeLayout.LayoutParams scrollParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
        debugLogScrollView.setLayoutParams(scrollParams);

        debugLogTextView = new TextView(this);
        debugLogTextView.setTextColor(0xFFFFFFFF);
        debugLogTextView.setTextSize(12);
        debugLogTextView.setPadding(16, 16, 16, 16);
        debugLogTextView.setText("Debug log initialized.\n");
        debugLogScrollView.addView(debugLogTextView);
        debugLogOverlay.setVisibility(View.GONE);
        debugLogOverlay.addView(debugLogScrollView);
        mainLayout.addView(debugLogOverlay);

        // Initialize slideshow history for random mode
        slideshowHistory = new SlideshowHistory();
        
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            private int previousPage = -1;
            private int scrollState = ViewPager.SCROLL_STATE_IDLE;
            
            @Override
            public void onPageScrollStateChanged(int state) {
                scrollState = state;
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                selectTransformer();
                
                // Track history for random mode navigation
                if (AppData.getRandomize() && !navigatingFromHistory) {
                    // Record this page in history if it's a new visit
                    if (position != lastRecordedPage) {
                        slideshowHistory.recordPageVisit(position);
                        lastRecordedPage = position;
                    }
                }
                navigatingFromHistory = false;
                
                // Detect manual swipe back (user swiped right/backward)
                if (previousPage != -1 && position < previousPage && AppData.getRandomize() && paused) {
                    // User swiped back while paused in random mode - try to go to history
                    if (slideshowHistory.canGoBack()) {
                        int historyPage = slideshowHistory.goBack();
                        if (historyPage != -1 && historyPage != position) {
                            navigatingFromHistory = true;
                            lastRecordedPage = historyPage;
                            pager.setCurrentItem(historyPage, false);
                        }
                    }
                }
                previousPage = position;
            }
        });

        //getlastpage
        currentPage = AppData.getCurrentPage();
        if(pager.getAdapter().getCount() < currentPage){
            currentPage = 1;
        }

        System.out.println("STARTING PAGE  " + currentPage);

        rightToLeft = AppData.getDirection();

        new AlarmScheduler().scheduleAlarm();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Show dialog explaining why we need the permission
                new AlertDialog.Builder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("PicFrame needs access to all files to sync and display your photos. Please grant 'All files access' permission in the next screen.")
                    .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                            } catch (Exception e) {
                                // Fallback if the specific intent doesn't work
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(MainActivity.this, "Storage permission is required for syncing photos", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setCancelable(false)
                    .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_SELECT_PHOTO && resultCode == Activity.RESULT_OK && data != null) {
			Uri selectedImageUri = data.getData();
			if (selectedImageUri != null && mFilePaths != null) {
				String selectedPath = getPathFromUri(selectedImageUri);
				if (selectedPath != null) {
					// Find the index of the selected photo in the current file list
					int index = -1;
					for (int i = 0; i < mFilePaths.size(); i++) {
						if (mFilePaths.get(i).equals(selectedPath)) {
							index = i;
							break;
						}
					}
					if (index != -1) {
						pager.setCurrentItem(index, false);
						lastRecordedPage = index;
						if (!AppData.getRandomize()) {
							// If not randomized, continue slideshow from selected photo
							if (AppData.getSlideshow() && paused) {
								paused = false;
								setUpSlideShow();
								startSlideshowCountDown();
							}
						}
					} else {
						Toast.makeText(this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
					}
				} else {
					Toast.makeText(this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
				}
			}
		}
		if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				if (Environment.isExternalStorageManager()) {
					Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(this, "Storage permission denied. Syncing will not work.", Toast.LENGTH_LONG).show();
				}
			}
		}
    }


    // TODO: folderpicker for owncloud server folder                                ! L

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        startActivity(new Intent(this, SettingsActivity.class));
        return super.onMenuOpened(featureId, menu);
    }

    protected void onResume() {
                // Register debug log broadcast receiver
                IntentFilter debugLogFilter = new IntentFilter();
                debugLogFilter.addAction("picframe.at.picframe.DEBUG_LOG_MESSAGE");
                if (broadcastManager == null) {
                    broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
                }
                broadcastManager.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if ("picframe.at.picframe.DEBUG_LOG_MESSAGE".equals(intent.getAction())) {
                            String msg = intent.getStringExtra("debug_message");
                            if (msg != null) appendDebugLog(msg);
                        }
                    }
                }, debugLogFilter);
        super.onResume();

        debug("onResume");
        // refresh toolbar options (hide/show downloadNow)
        supportInvalidateOptionsMenu();
        if (AppData.getFirstAppStart()) {
            AppData.setFirstAppStart(false);
            AppData.setTutorial(true);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tutorial();
        // get localBroadcastManager instance to receive localBroadCasts
        if (broadcastManager == null) {
            broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        }
        // register broadcast receiver for UI update from service
        if (receiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Keys.ACTION_DOWNLOAD_FINISHED);
            filter.addAction(Keys.ACTION_PROGRESSUPDATE);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            receiver = new ResponseReceiver();
            broadcastManager.registerReceiver(receiver, filter);
        }

        if(GlobalPhoneFuncs.getFileList(AppData.getImagePath()).size() > 0) {
            if (!AppData.getImagePath().equals(mOldPath) || mOldRecursive != AppData.getRecursiveSearch()) {
                loadAdapter();
            }
        }

        updateFileList();

        // start on the page we left in onPause, unless it was the first or last picture (as this freezes the slideshow
        if(currentPage < pager.getAdapter().getCount() -1 && currentPage > 0) {
            pager.setCurrentItem(currentPage);
        }
        
        // Record initial page in history for random mode
        if (AppData.getRandomize() && slideshowHistory != null && slideshowHistory.isEmpty()) {
            int initialPage = pager.getCurrentItem();
            slideshowHistory.recordPageVisit(initialPage);
            lastRecordedPage = initialPage;
        }

        setUpSlideShow();

        if(AppData.getSlideshow() && !paused){
            startSlideshowCountDown();
        }
        
        // Start screen pinning if kiosk mode is enabled
        if (AppData.getKioskEnabled()) {
            startKioskLockTask();
            scheduleKioskRepin();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu == null || !menu.hasVisibleItems())
            return super.onPrepareOptionsMenu(menu);

        if (AppData.getSourceType() == AppData.sourceTypes.OwnCloud) {
            menu.findItem(R.id.action_download).setVisible(true);
        } else {
            menu.findItem(R.id.action_download).setVisible(false);
        }
        
        // Show exit option only when kiosk mode is enabled
        MenuItem exitItem = menu.findItem(R.id.action_exit_kiosk);
        if (exitItem != null) {
            exitItem.setVisible(AppData.getKioskEnabled());
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_photo:
                // Show a custom dialog to select a photo from all cached folders
                showCachedPhotoPicker();
                return true;
            case R.id.action_settings:
                // If kiosk mode is enabled, require password
                if (AppData.getKioskEnabled()) {
                    showKioskPasswordDialog(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        }
                    });
                } else {
                    startActivity(new Intent(this, SettingsActivity.class));
                }
                return true;
            case R.id.action_download:
                debug("dowdnload now clicked");
                showSyncProgress(getString(R.string.syncing), "", 0);
                Intent startDownloadIntent = new Intent(mContext, DownloadService.class);
                startDownloadIntent.setAction(Keys.ACTION_STARTDOWNLOAD);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(startDownloadIntent);
                } else {
                    startService(startDownloadIntent);
                }
                return true;
            case R.id.action_about:
                // If kiosk mode is enabled, require password for about screen too
                if (AppData.getKioskEnabled()) {
                    showKioskPasswordDialog(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(MainActivity.this, StatusActivity.class));
                        }
                    });
                } else {
                    startActivity(new Intent(this, StatusActivity.class));
                }
                return true;
            case R.id.action_exit_kiosk:
                // Require password to exit/unpin kiosk mode
                showKioskPasswordDialog(new Runnable() {
                    @Override
                    public void run() {
                        kioskExitAuthorized = true;
                        cancelKioskRepin();
                        stopKioskLockTask();
                        finish();
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    /**
     * Shows a password dialog for kiosk mode. If the password is correct, runs the provided action.
     * @param onSuccess The action to run if the password is correct
     */
    private void showKioskPasswordDialog(final Runnable onSuccess) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.kiosk_dialog_title);
        builder.setMessage(R.string.kiosk_dialog_message);
        
        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);
        
        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String enteredPassword = input.getText().toString();
                String correctPassword = AppData.getKioskPassword();
                if (enteredPassword.equals(correctPassword)) {
                    onSuccess.run();
                } else {
                    Toast.makeText(MainActivity.this, R.string.kiosk_dialog_wrong_password, Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        
        builder.show();
    }

    protected void onPause() {
        super.onPause();
        cancelSlideShowCoundown();
        cancelKioskRepin();
        mOldPath = AppData.getImagePath();
        mOldRecursive = AppData.getRecursiveSearch();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // unregister receiver, because if the activity is not in focus, we want no UI updates
        broadcastManager.unregisterReceiver(receiver);
        receiver = null;
        if(click_on_settings_dialog != null){
            click_on_settings_dialog.cancel();
            click_on_settings_dialog.dismiss();
        }
        currentPage = pager.getCurrentItem();
    }

    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce) {
            // If kiosk mode is enabled, show password dialog instead of exiting
            if (AppData.getKioskEnabled()) {
                mDoubleBackToExitPressedOnce = false;
                showKioskPasswordDialog(new Runnable() {
                    @Override
                    public void run() {
                        kioskExitAuthorized = true;
                        cancelKioskRepin();
                        stopKioskLockTask();
                        MainActivity.super.onBackPressed();
                    }
                });
            } else {
                super.onBackPressed();
            }
        } else {
            this.mDoubleBackToExitPressedOnce = true;
            Toast.makeText(this, R.string.main_toast_exitmsg, Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDoubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }
    
    /**
     * Called when the user attempts to leave the app (home button, recent apps, etc.)
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // If kiosk mode is enabled, start screen pinning
        if (AppData.getKioskEnabled()) {
            startKioskLockTask();
        }
    }
    
    /**
     * Starts screen pinning (lock task) mode for kiosk functionality.
     * This prevents the user from leaving the app via home button, recent apps, etc.
     */
    private void startKioskLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && !am.isInLockTaskMode()) {
                try {
                    startLockTask();
                } catch (Exception e) {
                    // Lock task may fail if not enabled by device owner
                    // Fall back to bringing app back to foreground
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(MainActivity.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                        }
                    }, 50);
                }
            }
        }
    }
    
    /**
     * Stops screen pinning (lock task) mode.
     */
    private void stopKioskLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.isInLockTaskMode()) {
                try {
                    stopLockTask();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Schedules automatic re-pinning after KIOSK_REPIN_DELAY milliseconds.
     * This ensures the app re-pins if the user unpins via gesture.
     */
    private void scheduleKioskRepin() {
        if (kioskRepinHandler == null) {
            kioskRepinHandler = new Handler();
        }
        if (kioskRepinRunnable == null) {
            kioskRepinRunnable = new Runnable() {
                @Override
                public void run() {
                    if (AppData.getKioskEnabled() && !kioskExitAuthorized) {
                        startKioskLockTask();
                        // Schedule the next check
                        kioskRepinHandler.postDelayed(this, KIOSK_REPIN_DELAY);
                    }
                }
            };
        }
        // Remove any pending callbacks and schedule fresh
        kioskRepinHandler.removeCallbacks(kioskRepinRunnable);
        kioskRepinHandler.postDelayed(kioskRepinRunnable, KIOSK_REPIN_DELAY);
    }
    
    /**
     * Cancels the scheduled kiosk re-pin.
     */
    private void cancelKioskRepin() {
        if (kioskRepinHandler != null && kioskRepinRunnable != null) {
            kioskRepinHandler.removeCallbacks(kioskRepinRunnable);
        }
    }

    public void showActionBar() {
        if (this.getSupportActionBar() != null) {
            this.getSupportActionBar().show();
        }
        if (actionbarHideHandler != null) {
            actionbarHideHandler.removeCallbacksAndMessages(null);
            actionbarHideHandler = null;
        }
        actionbarHideHandler = new Handler();
        actionbarHideHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getSupportActionBar().hide();
            }
        }, 2500);
    }

    private void pageSwitcher() {
        if (imagePagerAdapter.getCount() > 0 && !paused) {
            int localpage = pager.getCurrentItem();
            // switch diretion if extremity of slideshow is reacher
            if (localpage >= imagePagerAdapter.getCount() - 1) {
                rightToLeft = false;
            } else if (localpage == 0) {
                rightToLeft = true;
            }
            if (rightToLeft) {
                localpage++;
            } else {
                localpage--;
            }
            pager.setCurrentItem(localpage, true);
        }
    }

    public static Context getContext() {
        return mContext;
    }

    @Override
    protected void onStop(){
        super.onStop();
        // Save the current Page to resume after next start
        // maybe not right here will test
        AppData.setCurrentPage(currentPage);
        debug("SAVING PAGE  " + currentPage);

        // Save the direction of the pageviewer
        AppData.setDirection(rightToLeft);
    }

    private void setUpSlideShow(){
        if (AppData.getSlideshow()){
            pager.setScrollDurationFactor(8);
//            pager.setPagingEnabled(false);
        }
        else{
            pager.setScrollDurationFactor(3);
//            pager.setPagingEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelSlideShowCoundown();
    }

    private class ImagePagerAdapter extends PagerAdapter {
        //        private List<String> mFilePaths;
        private Activity activity;
        private LayoutInflater inflater;
        private ImageView imgDisplay;
        private int localpage;
//        private int size;

        public ImagePagerAdapter(Activity activity) {
            this.activity = activity;
            updateSettings();
        }

        @Override
        public int getCount() {
            return size;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }


        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            inflater = (LayoutInflater) activity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View viewLayout = inflater.inflate(R.layout.fullscreen_layout, container,
                    false);

            imgDisplay = (ImageView) viewLayout.findViewById(R.id.photocontainer);

            this.localpage = position;
            android.graphics.Bitmap bitmap = null;
            if(!showExamplePictures){
                bitmap = EXIF_helper.decodeFile(mFilePaths.get(this.localpage), mContext);
                imgDisplay.setImageBitmap(bitmap);
            } else {
                String currentImage = "ex" + this.localpage;
                int currentImageID = mContext.getResources().getIdentifier(currentImage, "drawable", mContext.getPackageName());
                imgDisplay.setImageResource(currentImageID);
            }

            // Determine scale type based on orientation match
            if (AppData.getScaling()) {
                boolean shouldCrop = false;
                if (bitmap != null) {
                    // Get screen dimensions
                    android.view.Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    int screenWidth = display.getWidth();
                    int screenHeight = display.getHeight();
                    boolean screenIsLandscape = screenWidth > screenHeight;
                    // Get image dimensions
                    boolean imageIsLandscape = bitmap.getWidth() > bitmap.getHeight();
                    // Only crop if orientations match
                    shouldCrop = (screenIsLandscape == imageIsLandscape);
                }
                if (shouldCrop) {
                    imgDisplay.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    imgDisplay.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
            } else {
                imgDisplay.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }

            // Configure overlays - use combined overlay when both are at same position
            TextView dateTimeOverlay = (TextView) viewLayout.findViewById(R.id.dateTimeOverlay);
            TextView locationOverlay = (TextView) viewLayout.findViewById(R.id.locationOverlay);
            
            // Get location if enabled
            String location = null;
            if (AppData.getLocationEnabled() && !showExamplePictures) {
                int locationFormat = AppData.getLocationFormat();
                location = EXIF_helper.getLocationFromImage(mFilePaths.get(this.localpage), locationFormat);
                if (location != null && location.isEmpty()) location = null;
            }
            
            // Get date/time string if enabled
            String dateTimeText = null;
            if (AppData.getOverlayEnabled()) {
                String filePath = showExamplePictures ? null : mFilePaths.get(this.localpage);
                dateTimeText = getDateTimeString(filePath);
            }
            
            // Check if both are at the same position
            boolean samePosition = (AppData.getOverlayPosition() == AppData.getLocationPosition());
            
            if (samePosition && location != null && dateTimeText != null) {
                // Combine into single overlay (location on top, date below)
                String combinedText = location + "\n" + dateTimeText;
                dateTimeOverlay.setVisibility(View.VISIBLE);
                locationOverlay.setVisibility(View.GONE);
                configureCombinedOverlay(dateTimeOverlay, combinedText);
            } else {
                // Show separately
                if (dateTimeText != null) {
                    dateTimeOverlay.setVisibility(View.VISIBLE);
                    configureDateTimeOverlay(dateTimeOverlay, dateTimeText);
                } else {
                    dateTimeOverlay.setVisibility(View.GONE);
                }
                
                if (location != null) {
                    locationOverlay.setVisibility(View.VISIBLE);
                    configureLocationOverlay(locationOverlay, location);
                } else {
                    locationOverlay.setVisibility(View.GONE);
                }
            }

            imgDisplay.setOnTouchListener(new Gestures(getApplicationContext()) {
                @Override
                public void onSwipeBottom() {
                    showActionBar();
                }

                @Override
                public void onSwipeTop() {
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().hide();
                    }
                }
                @Override
                public void onTap() {
                    if (AppData.getSlideshow()) {
                        paused = !paused;

                        if (paused) {
                            cancelSlideShowCoundown();
//                            pager.setPagingEnabled(true);
                            if (mPause != null)
                                mPause.setVisibility(View.VISIBLE);

//                            remainingDisplayTime = 4; // TODO: comment in once we show remaing alarmtime
//                            if(settingsObj.getDisplayTime() >= 60){
//                                String remainingTimeString = String.valueOf(remainingDisplayTime);
//                                TextView textView = (TextView) findViewById(R.id.remaining_time_value);
//                                textView.setText(remainingTimeString);
//                                mRemainingTimeLayout.setVisibility(View.VISIBLE);
//                            }

                            showActionBar();
                        } else {
                            startSlideshowCountDown();
                            mPause.setVisibility(View.INVISIBLE);
/*                            if(position < pager.getAdapter().getCount() -1 && position > 0) {
                                pager.setCurrentItem(position);
                                page=position;
                                debug("position in range: "+position);
                            } else {
                                debug("not in range: "+position);
                            }
*/             //               pager.setPagingEnabled(false);
                            if (mRemainingTimeLayout.getVisibility() == View.VISIBLE)
                                mRemainingTimeLayout.setVisibility(View.INVISIBLE);
                        }
                    }
                }
            });
            container.addView(viewLayout);
            return viewLayout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
        }

        private void updateSettings() {
            mFilePaths = GlobalPhoneFuncs.getFileList(AppData.getImagePath());
            //setUp.notifyDataSetChanged();
            setSize();
        }

        public int getPage(){
            return this.localpage;
        }
    }

    /**
     * Gets the formatted date/time string for a photo
     * @param filePath Path to the image file, or null for current date
     * @return Formatted date/time string, or null if date couldn't be extracted from photo
     */
    private String getDateTimeString(String filePath) {
        java.util.Date photoDate = null;
        if (filePath != null) {
            photoDate = EXIF_helper.getDateFromImage(filePath);
        }
        // If we couldn't get the date from the photo, don't show anything
        if (photoDate == null) {
            return null;
        }
        
        int format = AppData.getOverlayFormat();
        java.text.SimpleDateFormat sdf;
        switch (format) {
            case 0: sdf = new java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()); break;
            case 1: sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()); break;
            case 2: sdf = new java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()); break;
            case 3: sdf = new java.text.SimpleDateFormat("dd MMMM yyyy  HH:mm", java.util.Locale.getDefault()); break;
            case 4: sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()); break;
            default: sdf = new java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()); break;
        }
        return sdf.format(photoDate);
    }

    /**
     * Applies common overlay styling (size, color, shadow)
     */
    private void applyOverlayStyle(TextView overlay) {
        int size = AppData.getOverlaySize();
        float textSizeSp;
        switch (size) {
            case 0: textSizeSp = 14f; break;
            case 1: textSizeSp = 18f; break;
            case 2: textSizeSp = 24f; break;
            case 3: textSizeSp = 32f; break;
            default: textSizeSp = 18f; break;
        }
        overlay.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp);

        int style = AppData.getOverlayStyle();
        switch (style) {
            case 0:
                overlay.setTextColor(android.graphics.Color.WHITE);
                overlay.setShadowLayer(0, 0, 0, android.graphics.Color.TRANSPARENT);
                break;
            case 1:
                overlay.setTextColor(android.graphics.Color.BLACK);
                overlay.setShadowLayer(0, 0, 0, android.graphics.Color.TRANSPARENT);
                break;
            case 2:
                overlay.setTextColor(android.graphics.Color.WHITE);
                overlay.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
                break;
            case 3:
                overlay.setTextColor(android.graphics.Color.BLACK);
                overlay.setShadowLayer(3, 1, 1, android.graphics.Color.WHITE);
                break;
            default:
                overlay.setTextColor(android.graphics.Color.WHITE);
                overlay.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
                break;
        }
    }

    /**
     * Applies position to an overlay
     */
    private void applyOverlayPosition(TextView overlay, int position) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) overlay.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.BELOW, 0);
        params.addRule(RelativeLayout.ABOVE, 0);

        float density = overlay.getContext().getResources().getDisplayMetrics().density;
        int marginPx = (int) (16 * density);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);

        switch (position) {
            case 0: params.addRule(RelativeLayout.ALIGN_PARENT_TOP); params.addRule(RelativeLayout.ALIGN_PARENT_LEFT); break;
            case 1: params.addRule(RelativeLayout.ALIGN_PARENT_TOP); params.addRule(RelativeLayout.CENTER_HORIZONTAL); break;
            case 2: params.addRule(RelativeLayout.ALIGN_PARENT_TOP); params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT); break;
            case 3: params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); params.addRule(RelativeLayout.ALIGN_PARENT_LEFT); break;
            case 4: params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); params.addRule(RelativeLayout.CENTER_HORIZONTAL); break;
            case 5: default: params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT); break;
        }
        overlay.setLayoutParams(params);
    }

    /**
     * Configures a combined overlay with both location and date/time
     */
    private void configureCombinedOverlay(TextView overlay, String text) {
        overlay.setText(text);
        applyOverlayStyle(overlay);
        applyOverlayPosition(overlay, AppData.getOverlayPosition());
    }

    /**
     * Configures the date/time overlay (standalone)
     */
    private void configureDateTimeOverlay(TextView overlay, String dateTimeText) {
        overlay.setText(dateTimeText);
        applyOverlayStyle(overlay);
        applyOverlayPosition(overlay, AppData.getOverlayPosition());
    }

    /**
     * Configures the location overlay (standalone)
     */
    private void configureLocationOverlay(TextView overlay, String location) {
        overlay.setText(location);
        applyOverlayStyle(overlay);
        applyOverlayPosition(overlay, AppData.getLocationPosition());
    }

    public void updateFileList() {
        if(AppData.getImagePath().equals("")) {
            showExamplePictures = true;
            Toast.makeText(mContext,R.string.main_toast_noFolderPathSet, Toast.LENGTH_SHORT).show();
        } else {
            mFilePaths = GlobalPhoneFuncs.getFileList(AppData.getImagePath());
            showExamplePictures = mFilePaths.isEmpty() || mFilePaths.size() <= 0;
            if (showExamplePictures) {
                Toast.makeText(mContext,R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
            } else {
                // Update the date cache for the new file list
                // Always maintain cache regardless of current sort order so it's ready when needed
                PhotoDateCache cache = PhotoDateCache.getInstance(getApplicationContext());
                cache.updateCacheAsync(mFilePaths, new PhotoDateCache.CacheUpdateListener() {
                    @Override
                    public void onProgress(int processed, int total) {
                        // Could show progress in debug log if needed
                    }
                    
                    @Override
                    public void onComplete() {
                        // Cache is ready, could trigger a resort if needed
                    }
                });
            }
        }
        setSize(); // size is count of images in folder, or constant if example pictures are used
        imagePagerAdapter.notifyDataSetChanged();
        
        // Clear history when file list changes (new shuffle order)
        if (slideshowHistory != null) {
            slideshowHistory.clear();
            lastRecordedPage = -1;
        }
    }


    private void loadAdapter(){
        imagePagerAdapter = new ImagePagerAdapter(MainActivity.this);
//        pager.setPagingEnabled(true);
//        setUp = new DisplayImages(MainActivity.this);
        try {
            pager.setAdapter(imagePagerAdapter);
            currentPage = imagePagerAdapter.getPage();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void enableGestures(){
        mainLayout.setOnTouchListener(new Gestures(getApplicationContext()) {
            @Override
            public void onSwipeBottom() {
                showActionBar();
            }

            @Override
            public void onSwipeTop() {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            }
        });
    }

    private boolean checkForProblemsAndShowToasts() {
        // OwnCloud or Dropbox selected
        if (!AppData.sourceTypes.ExternalSD.equals(AppData.getSourceType())) {
            // if no write rights, we don't need to download
            if (!GlobalPhoneFuncs.isExternalStorageWritable()) {
                Toast.makeText(this, R.string.main_toast_noSDWriteRights, Toast.LENGTH_SHORT).show();
            } else {
                // If no Username set although source is not SD Card
                if ("".equals(AppData.getUserName()) || "".equals(AppData.getUserPassword())) {
                    Toast.makeText(this, R.string.main_toast_noUsernameSet, Toast.LENGTH_SHORT).show();
                } else {
                    debug("username and pw set");
                    // Try to connect & login to selected source server
                    if (AppData.sourceTypes.OwnCloud.equals(AppData.getSourceType())) {
                        debug("trying OC check");
                        //startConnectionCheck();
                        Intent startDownloadIntent2 = new Intent(mContext, DownloadService.class);
                        startDownloadIntent2.setAction(Keys.ACTION_STARTDOWNLOAD);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(startDownloadIntent2);
                        } else {
                            startService(startDownloadIntent2);
                        }
                        return true;
                    }// else if (AppData.getSourceType().equals(AppData.sourceTypes.Dropbox))
                    {
                        // TODO: Dropbox checks go here
                    }
                }
            }
            // SD Card selected
        } else {
            if (!GlobalPhoneFuncs.isExternalStorageReadable()) {
                // If no read rights for SD although source is SD Card
                Toast.makeText(this, R.string.main_toast_noSDReadRights, Toast.LENGTH_SHORT).show();
            } else {
                if (!GlobalPhoneFuncs.hasAllowedFiles()) {
                    Toast.makeText(this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    public void selectTransformer(){
        if(AppData.getSlideshow() && AppData.getTransitionStyle() == 11){
            pager.setPageTransformer(true,transformers.get(random()));
        } else if(AppData.getSlideshow()){
            pager.setPageTransformer(true,transformers.get(AppData.getTransitionStyle()));
        } else {
            pager.setPageTransformer(true,new ZoomOutPageTransformer());
        }
    }

    private int random(){
        //Random from 0 to 13
        return (int)(Math.random() * 11);
    }

    private void setSize(){
        if(!showExamplePictures)
            size = mFilePaths.size();
        else
            size = nbOfExamplePictures;
    }

    private Intent getSettingsActivityIntent(){
        return new Intent(this, SettingsActivity.class);
    }

    private void initializeTransitions(){
        transformers = new ArrayList<>();
        this.transformers.add(new AccordionTransformer());
        this.transformers.add(new BackgroundToForegroundTransformer());
        this.transformers.add(new CubeOutTransformer());
        this.transformers.add(new DrawFromBackTransformer());
        this.transformers.add(new FadeInFadeOutTransformer());
        this.transformers.add(new FlipVerticalTransformer());
        this.transformers.add(new ForegroundToBackgroundTransformer());
        this.transformers.add(new RotateDownTransformer());
        this.transformers.add(new StackTransformer());
        this.transformers.add(new ZoomInTransformer());
        this.transformers.add(new ZoomOutPageTransformer());
    }

    private void tutorial (){
        if (!AppData.getTutorial()) {
            return;
        }
        AlertDialog.Builder click_on_settings_dialog_builder = new AlertDialog.Builder(MainActivity.this);
        click_on_settings_dialog_builder
                .setMessage(R.string.main_dialog_tutorial_text)
                .setPositiveButton(R.string.main_dialog_tutorial_okButton,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        })
                .setNeutralButton(R.string.main_dialog_tutorial_dontShowAgainButton,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                AppData.setTutorial(false);
                            }
                        })
                .setNegativeButton(R.string.main_dialog_tutorial_openSettingsNowButton,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                                startActivity(getSettingsActivityIntent());
                            }
                        });
        click_on_settings_dialog = click_on_settings_dialog_builder.create();
        click_on_settings_dialog.getWindow().setGravity(Gravity.TOP | Gravity.START);
        click_on_settings_dialog.setCancelable(true);
        click_on_settings_dialog.show();
        showActionBar();
    }


    public class ResponseReceiver extends BroadcastReceiver {
        private int progressBroadCastsReceived = 0;
        public static final String BOOTED_MSG = "booted";
        private float percent;
        private boolean indeterminate;

        // to prevent instantiation
        private ResponseReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            debug("In onReceive!");
            if (intent != null) {
                // received an intent to update the viewpager
                if (Keys.ACTION_DOWNLOAD_FINISHED.equals(intent.getAction())) {
                    debug("received 'download_finished' action via broadcast");
                    hideSyncProgress();
                    progressBroadCastsReceived = 0;
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            updateFileList();
                        }
                    });
                } else if (Keys.ACTION_PROGRESSUPDATE.equals(intent.getAction())) {
                    progressBroadCastsReceived++;
                    final int progressPercent = intent.getIntExtra(Keys.MSG_PROGRESSUPDATE_PERCENT, 0);
                    Boolean indeterminate = intent.getBooleanExtra(Keys.MSG_PROGRESSUPDATE_INDITERMINATE, true);
                    final String fileName = intent.getStringExtra(Keys.MSG_PROGRESSUPDATE_FILENAME);
                    debug("received 'progress_update' - " +
                            progressPercent + "% - indeterminate?" + indeterminate + " file: " + fileName);
                    // Update the sync progress bar in the UI
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            updateSyncProgress(fileName != null ? fileName : "", progressPercent);
                        }
                    });
                    if (progressBroadCastsReceived > 2) {
                        new Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                updateFileList();
                            }
                        });
                        progressBroadCastsReceived = 0;
                    }
                }
            }
        }
    }

    private void startSlideshowCountDown() {
        debug("startSlideshowCountDown");
        if(remainingDisplayTime != 0 && remainingDisplayTime < AppData.getDisplayTime()) {
            debug("remainingDisplayTime: " + remainingDisplayTime + " < " + AppData.getDisplayTime()+", displayTime");
            countDownTimer = new CountDownTimer((AppData.getDisplayTime()-remainingDisplayTime)*1000, countdownIntervalInMilliseconds) {
                @Override
                public void onTick(long l) {
                    debug("unique tick!" + l / 1000);
                    remainingDisplayTime = l;
                }

                @Override
                public void onFinish() {
                    debug("done with this timer!");
                    pageSwitcher();
                    startRepeatingCountDowns();
                }
            }.start();
        } else {
            debug("no leftover displaytime!");
            startRepeatingCountDowns();
        }

    }

    private void startRepeatingCountDowns() {
        debug("startRepeatingCountDowns");
        countDownTimer = new CountDownTimer(AppData.getDisplayTime()*1000, countdownIntervalInMilliseconds) {
            @Override
            public void onTick(long l) {
                remainingDisplayTime = l/1000;
            }

            @Override
            public void onFinish() {
                pageSwitcher();
                countDownTimer.start();
            }
        }.start();
    }

    private void cancelSlideShowCoundown(){
        /* remainingg display time is always imprecise, the real value located somewhere between
            0 and countdownIntervalInMilliseconds.
            If the display time is smaller than countdownIntervalInMilliseconds/1000,
            then the value of countdownIntervalInMilliseconds will never change, and resetting
            the value will be more accurate.
         */
        if(AppData.getDisplayTime() < countdownIntervalInMilliseconds/1000) {
            debug(AppData.getDisplayTime() + " < " + countdownIntervalInMilliseconds/1000);
            remainingDisplayTime = 0;
        }
        if(countDownTimer != null){
            countDownTimer.cancel();
        }
    }

    private void showCachedPhotoPicker() {
        showFolderPicker(AppData.getImagePath());
    }

    /**
     * Show a hierarchical folder/file picker dialog for the given directory.
     */
    private void showFolderPicker(String dirPath) {
        final java.io.File dir = new java.io.File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
            return;
        }
        java.io.File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
            return;
        }
        // Separate folders and files, folders first
        java.util.List<java.io.File> folders = new java.util.ArrayList<>();
        java.util.List<java.io.File> imageFiles = new java.util.ArrayList<>();
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                folders.add(f);
            } else if (isImageFile(f)) {
                imageFiles.add(f);
            }
        }
        
        // Sort folders by name
        java.util.Collections.sort(folders, new java.util.Comparator<java.io.File>() {
            @Override
            public int compare(java.io.File f1, java.io.File f2) {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        });
        
        // Sort image files based on current sort order
        int sortOrder = AppData.getSortOrder();
        if (sortOrder == 2) {
            // Sort by photo date/time using cache
            final PhotoDateCache cache = PhotoDateCache.getInstance(getApplicationContext());
            java.util.Collections.sort(imageFiles, new java.util.Comparator<java.io.File>() {
                @Override
                public int compare(java.io.File f1, java.io.File f2) {
                    java.util.Date date1 = cache.getCachedDate(f1.getAbsolutePath());
                    java.util.Date date2 = cache.getCachedDate(f2.getAbsolutePath());
                    
                    // If both have dates, compare them
                    if (date1 != null && date2 != null) {
                        return date1.compareTo(date2);
                    }
                    // If only one has a date, put it first
                    if (date1 != null) return -1;
                    if (date2 != null) return 1;
                    // If neither has a date, sort by filename
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });
        } else {
            // Sort by filename (for both filename and random modes)
            java.util.Collections.sort(imageFiles, new java.util.Comparator<java.io.File>() {
                @Override
                public int compare(java.io.File f1, java.io.File f2) {
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });
        }
        
        final java.util.List<java.io.File> allItems = new java.util.ArrayList<>();
        allItems.addAll(folders);
        allItems.addAll(imageFiles);
        CharSequence[] itemNames = new CharSequence[allItems.size()];
        PhotoDateCache cache = PhotoDateCache.getInstance(getApplicationContext());
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        
        for (int i = 0; i < allItems.size(); i++) {
            java.io.File f = allItems.get(i);
            if (f.isDirectory()) {
                itemNames[i] = "[" + f.getName() + "]";
            } else {
                // For image files, try to append date/time if available
                java.util.Date photoDate = cache.getCachedDate(f.getAbsolutePath());
                if (photoDate != null) {
                    itemNames[i] = f.getName() + "  (" + dateFormat.format(photoDate) + ")";
                } else {
                    itemNames[i] = f.getName();
                }
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(dirPath.equals(AppData.getImagePath()) ? getString(R.string.action_select_photo) : dir.getName());
        builder.setItems(itemNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                java.io.File selected = allItems.get(which);
                if (selected.isDirectory()) {
                    showFolderPicker(selected.getAbsolutePath());
                } else {
                    String selectedPath = selected.getAbsolutePath();
                    int index = -1;
                    if (mFilePaths != null) {
                        for (int i = 0; i < mFilePaths.size(); i++) {
                            if (mFilePaths.get(i).equals(selectedPath)) {
                                index = i;
                                break;
                            }
                        }
                    }
                    if (index != -1) {
                        pager.setCurrentItem(index, false);
                        lastRecordedPage = index;
                        if (!AppData.getRandomize()) {
                            if (AppData.getSlideshow() && paused) {
                                paused = false;
                                setUpSlideShow();
                                startSlideshowCountDown();
                                if (mPause != null) {
                                    mPause.setVisibility(View.GONE);
                                }
                            }
                        }
                    } else {
                        Toast.makeText(MainActivity.this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        if (!dirPath.equals(AppData.getImagePath())) {
            builder.setNegativeButton(R.string.action_back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    java.io.File parent = dir.getParentFile();
                    if (parent != null) {
                        showFolderPicker(parent.getAbsolutePath());
                    }
                }
            });
        } else {
            builder.setNegativeButton(android.R.string.cancel, null);
        }
        builder.show();
    }

    /**
     * Returns true if the file is an image (by extension).
     */
    private boolean isImageFile(java.io.File file) {
        String[] exts = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
        String name = file.getName().toLowerCase();
        for (String ext : exts) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }
				
	private String getPathFromUri(Uri uri) {
		// Try to get the real file path from the Uri
		// This is a simplified approach for local files; may need to be extended for SAF/other providers
		if (uri == null) return null;
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}
		// For content Uris, try to resolve to file path
		try {
			String[] proj = { android.provider.MediaStore.Images.Media.DATA };
			android.database.Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
			if (cursor != null) {
				int column_index = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA);
				if (cursor.moveToFirst()) {
					String path = cursor.getString(column_index);
					cursor.close();
					return path;
				}
				cursor.close();
			}
		} catch (Exception e) {
			// Ignore and fallback
		}
		// Fallback: try to get last path segment
		return uri.getPath();
	}

    private void debug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
            appendDebugLog(msg);
        }
    }
}
