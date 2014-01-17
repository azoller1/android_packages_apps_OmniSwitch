/*
 *  Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omniswitch.ui;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.MemInfoReader;
import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.SettingsActivity;
import org.omnirom.omniswitch.SwitchManager;
import org.omnirom.omniswitch.SwitchService;
import org.omnirom.omniswitch.TaskDescription;
import org.omnirom.omniswitch.Utils;
import org.omnirom.omniswitch.showcase.ShowcaseView;
import org.omnirom.omniswitch.showcase.ShowcaseView.OnShowcaseEventListener;

import android.app.ActivityManager;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

public class SwitchLayout implements OnShowcaseEventListener {
    private static final String TAG = "SwitchLayout";
    private static final boolean DEBUG = false;
    private final static String KEY_SHOWCASE_FAVORITE = "showcase_favorite_done";

    private WindowManager mWindowManager;
    private LayoutInflater mInflater;
    private HorizontalListView mRecentListHorizontal;
    private HorizontalListView mFavoriteListHorizontal;
    private ImageButton mLastAppButton;
    private ImageButton mKillAllButton;
    private ImageButton mKillOtherButton;
    private ImageButton mHomeButton;
    private ImageButton mSettingsButton;
    private RecentListAdapter mRecentListAdapter;
    private FavoriteListAdapter mFavoriteListAdapter;
    private List<TaskDescription> mLoadedTasks;
    private Context mContext;
    private SwitchManager mRecentsManager;
    private FrameLayout mPopupView;
    private boolean mShowing;
    private PopupMenu mPopup;
    private View mView;
    private LinearColorBar mRamUsageBar;
    private TextView mBackgroundProcessText;
    private TextView mForegroundProcessText;
    private Handler mHandler = new Handler();
    private ActivityManager.MemoryInfo mMemInfo = new ActivityManager.MemoryInfo();
    private MemInfoReader mMemInfoReader = new MemInfoReader();
    private long mSecServerMem;
    private List<String> mFavoriteList;
    private List<Drawable> mFavoriteIcons;
    private List<String> mFavoriteNames;
    private boolean mShowFavorites;
    private ShowcaseView mShowcaseView;
    private SharedPreferences mPrefs;
    private boolean mShowcaseDone;
    private float mOpenFavoriteY;
    private SwitchConfiguration mConfiguration;
    private ImageButton mOpenFavorite;
    private boolean mHasFavorites;
    private boolean[] mButtons;

    public class RecentListAdapter extends ArrayAdapter<TaskDescription> {

        public RecentListAdapter(Context context, int resource,
                List<TaskDescription> values) {
            super(context, R.layout.recent_item_horizontal, resource, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = null;
            TaskDescription ad = mLoadedTasks.get(position);

            rowView = mInflater.inflate(R.layout.recent_item_horizontal,
                    parent, false);
            final TextView item = (TextView) rowView
                    .findViewById(R.id.recent_item);
            if (mConfiguration.mShowLabels) {
                item.setText(ad.getLabel());
            }
            item.setMaxWidth(mConfiguration.mHorizontalMaxWidth);
            item.setCompoundDrawablesWithIntrinsicBounds(null,
                    ad.getIcon(), null, null);

            return rowView;
        }
    }

    public class FavoriteListAdapter extends ArrayAdapter<String> {

        public FavoriteListAdapter(Context context, int resource,
                List<String> values) {
            super(context, R.layout.favorite_item, resource, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = null;
            rowView = mInflater.inflate(R.layout.favorite_item, parent, false);
            final TextView item = (TextView) rowView
                    .findViewById(R.id.favorite_item);
            if (mConfiguration.mShowLabels) {
                item.setText(mFavoriteNames.get(position));
            }
            item.setMaxWidth(mConfiguration.mHorizontalMaxWidth);
            item.setCompoundDrawablesWithIntrinsicBounds(null,
                    mFavoriteIcons.get(position), null, null);
            return rowView;
        }
    }

    public void setRecentsManager(SwitchManager manager) {
        mRecentsManager = manager;
    }

    public SwitchLayout(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mConfiguration = SwitchConfiguration.getInstance(mContext);

        mInflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLoadedTasks = new ArrayList<TaskDescription>();
        mRecentListAdapter = new RecentListAdapter(mContext,
                android.R.layout.simple_list_item_multiple_choice, mLoadedTasks);
        mFavoriteList = new ArrayList<String>();
        mFavoriteListAdapter = new FavoriteListAdapter(mContext,
                android.R.layout.simple_list_item_multiple_choice,
                mFavoriteList);

        final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mMemInfo);
        String sClassName = "android.app.ActivityManager";
        try {
            Class classToInvestigate = Class.forName(sClassName);
            Class[] classes = classToInvestigate.getDeclaredClasses();
            for (int i = 0; i < classes.length; i++) {
                Class c = classes[i];
                if (c.getName()
                        .equals("android.app.ActivityManager$MemoryInfo")) {
                    String strNewFieldName = "secondaryServerThreshold";
                    Field field = c.getField(strNewFieldName);
                    mSecServerMem = field.getLong(mMemInfo);
                    break;
                }
            }
        } catch (ClassNotFoundException e) {
        } catch (NoSuchFieldException e) {
        } catch (Exception e) {
        }
    }

    private void createView() {
        mView = mInflater.inflate(R.layout.recents_list_horizontal, null,
                false);
        mRecentListHorizontal = (HorizontalListView) mView
                .findViewById(R.id.recent_list_horizontal);

        mRecentListHorizontal
        .setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    View view, int position, long id) {
                if(DEBUG){
                    Log.d(TAG, "onItemClick");
                }
                TaskDescription task = mLoadedTasks.get(position);
                mRecentsManager.switchTask(task);
            }
        });

        mRecentListHorizontal
        .setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent,
                    View view, int position, long id) {
                if(DEBUG){
                    Log.d(TAG, "onItemLongClick");
                }
                TaskDescription task = mLoadedTasks.get(position);
                handleLongPress(task, view);
                return true;
            }
        });

        SwipeDismissHorizontalListViewTouchListener touchListener = new SwipeDismissHorizontalListViewTouchListener(
                mRecentListHorizontal,
                new SwipeDismissHorizontalListViewTouchListener.DismissCallbacks() {
                    public void onDismiss(HorizontalListView listView,
                            int[] reverseSortedPositions) {
                        for (int position : reverseSortedPositions) {
                            TaskDescription ad = mRecentListAdapter
                                    .getItem(position);
                            mRecentsManager.killTask(ad);
                            break;
                        }
                    }

                    @Override
                    public boolean canDismiss(int position) {
                        return true;
                    }
                });

        mRecentListHorizontal.setSwipeListener(touchListener);
        mRecentListHorizontal.setAdapter(mRecentListAdapter);

        mOpenFavorite = (ImageButton) mView
                .findViewById(R.id.openFavorites);

        mOpenFavorite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mShowFavorites = !mShowFavorites;
                mOpenFavorite.setImageDrawable(mContext.getResources().getDrawable(
                        mShowFavorites ? R.drawable.arrow_up
                                : R.drawable.arrow_down));
                if (mConfiguration.mAnimate) {
                    mFavoriteListHorizontal
                    .startAnimation(mShowFavorites ? getShowFavoriteAnimation()
                            : getHideFavoriteAnimation());
                } else {
                    mFavoriteListHorizontal
                    .setVisibility(mShowFavorites ? View.VISIBLE
                            : View.GONE);
                }
            }
        });

        mFavoriteListHorizontal = (HorizontalListView) mView
                .findViewById(R.id.favorite_list_horizontal);

        mFavoriteListHorizontal
        .setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    View view, int position, long id) {
                if(DEBUG){
                    Log.d(TAG, "onItemClick");
                }
                String intent = mFavoriteList.get(position);
                mRecentsManager.startIntentFromtString(intent);
            }
        });
        mFavoriteListHorizontal.setAdapter(mFavoriteListAdapter);

        mHomeButton = (ImageButton) mView.findViewById(R.id.home);
        mHomeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRecentsManager.dismissAndGoHome();
            }
        });
        mHomeButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, 
                        mContext.getResources().getString(R.string.home_help), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mLastAppButton = (ImageButton) mView.findViewById(R.id.lastApp);
        mLastAppButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRecentsManager.toggleLastApp();
            }
        });
        mLastAppButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, 
                        mContext.getResources().getString(R.string.toogle_last_app_help), Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        mKillAllButton = (ImageButton) mView.findViewById(R.id.killAll);
        mKillAllButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRecentsManager.killAll();
            }
        });
        mKillAllButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, 
                        mContext.getResources().getString(R.string.kill_all_apps_help), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mKillOtherButton = (ImageButton) mView.findViewById(R.id.killOther);
        mKillOtherButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRecentsManager.killOther();
            }
        });
        mKillOtherButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, 
                        mContext.getResources().getString(R.string.kill_other_apps_help), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mSettingsButton = (ImageButton) mView.findViewById(R.id.settings);
        mSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent hideRecent = new Intent(
                        SwitchService.RecentsReceiver.ACTION_HIDE_OVERLAY);
                mContext.sendBroadcast(hideRecent);

                Intent mainActivity = new Intent(mContext,
                        SettingsActivity.class);
                mainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                mContext.startActivity(mainActivity);
            }
        });
        mSettingsButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, 
                        mContext.getResources().getString(R.string.settings_help), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mRamUsageBar = (LinearColorBar) mView.findViewById(R.id.ram_usage_bar);
        mForegroundProcessText = (TextView) mView
                .findViewById(R.id.foregroundText);
        mBackgroundProcessText = (TextView) mView
                .findViewById(R.id.backgroundText);

        mPopupView = new FrameLayout(mContext);
        mPopupView.setBackgroundColor(Color.BLACK);

        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mShowing) {
                    if(DEBUG){
                        Log.d(TAG, "onTouch");
                    }
                    Intent hideRecent = new Intent(
                            SwitchService.RecentsReceiver.ACTION_HIDE_OVERLAY);
                    mContext.sendBroadcast(hideRecent);
                }
                return true;
            }
        });
        mPopupView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (mShowing) {
                    if(DEBUG){
                        Log.d(TAG, "onKey");
                    }
                    Intent hideRecent = new Intent(
                            SwitchService.RecentsReceiver.ACTION_HIDE_OVERLAY);
                    mContext.sendBroadcast(hideRecent);
                }
                return true;
            }
        });
    }

    private void initView(){
        mRamUsageBar.setVisibility(mConfiguration.mShowRambar ? View.VISIBLE : View.GONE);
        mFavoriteListHorizontal.setLayoutParams(getListviewParams());
        mRecentListHorizontal.setLayoutParams(getListviewParams());

        mOpenFavorite.setVisibility(mHasFavorites ? View.VISIBLE : View.GONE);
        if (!mHasFavorites){
            mShowFavorites = false;
        }
        mOpenFavorite.setImageDrawable(mContext.getResources().getDrawable(
                mShowFavorites ? R.drawable.arrow_up : R.drawable.arrow_down));
        mFavoriteListHorizontal.setVisibility(mShowFavorites ? View.VISIBLE : View.GONE);

        if(mHasFavorites && !mShowcaseDone){
            mOpenFavorite.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mOpenFavorite.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int[] location = new int[2];
                    mOpenFavorite.getLocationOnScreen(location);
                    mOpenFavoriteY = location[1];
                }
            });
        }
        
        mKillAllButton.setVisibility(mButtons[SettingsActivity.BUTTON_KILL_ALL] ? View.VISIBLE : View.GONE);
        mKillOtherButton.setVisibility(mButtons[SettingsActivity.BUTTON_KILL_OTHER] ? View.VISIBLE : View.GONE);
        mLastAppButton.setVisibility(mButtons[SettingsActivity.BUTTON_TOGGLE_APP] ? View.VISIBLE : View.GONE);
        mHomeButton.setVisibility(mButtons[SettingsActivity.BUTTON_HOME] ? View.VISIBLE : View.GONE);
        mSettingsButton.setVisibility(mButtons[SettingsActivity.BUTTON_SETTINGS] ? View.VISIBLE : View.GONE);
    }

    public synchronized void show() {
        if (mShowing) {
            return;
        }

        if (mPopupView == null){
            createView();
        }
        
        initView();

        mPopupView.getBackground().setAlpha(0);

        try {
            mWindowManager.addView(mPopupView, getParams());
        } catch(java.lang.IllegalStateException e){
            // something went wrong - try to recover here
            mWindowManager.removeView(mPopupView);
            mPopupView.removeAllViews();
            mWindowManager.addView(mPopupView, getParams());
        }
        
        mPopupView.addView(mView);

        if (mConfiguration.mAnimate) {
            mView.startAnimation(getShowAnimation());
        } else {
            showDone();
        }
        if(mHasFavorites && !mShowcaseDone){
            mPopupView.postDelayed(new Runnable(){
                @Override
                public void run() {
                    startShowcaseFavorite();
                }}, 200);
        }
    }

    private void showDone(){
        mPopupView.getBackground().setAlpha((int) (255 * mConfiguration.mBackgroundOpacity));
        mPopupView.setFocusableInTouchMode(true);
        mShowing = true;
        Intent intent = new Intent(
                SwitchService.RecentsReceiver.ACTION_OVERLAY_SHOWN);
        mContext.sendBroadcast(intent);
    }

    private Animation getShowAnimation() {
        int animId = R.anim.slide_right_in;

        if (mConfiguration.mLocation == 1) {
            animId = R.anim.slide_left_in;
        }
        Animation animation = AnimationUtils.loadAnimation(mContext, animId);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                showDone();
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        return animation;
    }

    private Animation getHideAnimation() {
        int animId = R.anim.slide_right_out;

        if (mConfiguration.mLocation == 1) {
            animId = R.anim.slide_left_out;
        }

        Animation animation = AnimationUtils.loadAnimation(mContext, animId);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                // to avoid the "Attempting to destroy the window while drawing"
                // error
                mPopupView.post(new Runnable() {
                    @Override
                    public void run() {
                        hideDone();
                    }
                });
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        return animation;
    }

    private Animation getShowFavoriteAnimation() {
        int animId = R.anim.slide_down;
        Animation animation = AnimationUtils.loadAnimation(mContext, animId);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
                mFavoriteListHorizontal.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        return animation;
    }

    private Animation getHideFavoriteAnimation() {
        int animId = R.anim.slide_up;
        Animation animation = AnimationUtils.loadAnimation(mContext, animId);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mFavoriteListHorizontal.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        return animation;
    }

    private void hideDone() {
        mWindowManager.removeView(mPopupView);
        mPopupView.removeAllViews();
        mShowing = false;
    }

    public synchronized void hide() {
        if (!mShowing) {
            return;
        }
        
        if (mPopup != null) {
            mPopup.dismiss();
        }

        mPopupView.getBackground().setAlpha(0);
        if (mConfiguration.mAnimate) {
            mView.startAnimation(getHideAnimation());
        } else {
            hideDone();
        }
    }

    private LinearLayout.LayoutParams getListviewParams(){
        return new LinearLayout.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            mConfiguration.mHorizontalScrollerHeight);
    }

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                0,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.y = mConfiguration.getCurrentOffsetStart() + mConfiguration.mHandleHeight / 2 - mConfiguration.mHorizontalScrollerHeight / 2 - mConfiguration.mIconSize;

        return params;
    }

    public void update(List<TaskDescription> taskList) {
        if(DEBUG){
            Log.d(TAG, "update");
        }
        mLoadedTasks.clear();
        mLoadedTasks.addAll(taskList);
        mRecentListAdapter.notifyDataSetChanged();
        mHandler.post(updateRamBarTask);
    }

    private void handleLongPress(final TaskDescription ad, View view) {
        final PopupMenu popup = new PopupMenu(mContext, view);
        mPopup = popup;
        popup.getMenuInflater().inflate(R.menu.recent_popup_menu,
                popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.recent_remove_item) {
                    mRecentsManager.killTask(ad);
                } else if (item.getItemId() == R.id.recent_inspect_item) {
                    startApplicationDetailsActivity(ad.getPackageName());
                } else {
                    return false;
                }
                return true;
            }
        });
        popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
            public void onDismiss(PopupMenu menu) {
                mPopup = null;
            }
        });
        popup.show();
    }

    private void startApplicationDetailsActivity(String packageName) {
        Intent hideRecent = new Intent(
                SwitchService.RecentsReceiver.ACTION_HIDE_OVERLAY);
        mContext.sendBroadcast(hideRecent);

        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(
                        "package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext)
                .addNextIntentWithParentStack(intent).startActivities();
    }

    public void handleSwipe(TaskDescription ad) {
        mRecentsManager.killTask(ad);
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if(DEBUG){
            Log.d(TAG, "updatePrefs");
        }
        mFavoriteList.clear();
        String favoriteListString = prefs.getString(SettingsActivity.PREF_FAVORITE_APPS, "");
        Utils.parseFavorites(favoriteListString, mFavoriteList);

        mHasFavorites = mFavoriteList.size() != 0;
        updateFavorites();
        mFavoriteListAdapter.notifyDataSetChanged();

        mButtons = Utils.buttonStringToArry(prefs.getString(SettingsActivity.PREF_BUTTONS, SettingsActivity.PREF_BUTTON_DEFAULT));
    }

    private final Runnable updateRamBarTask = new Runnable() {
        @Override
        public void run() {
            if (!mConfiguration.mShowRambar || mRamUsageBar == null) {
                return;
            }
            mMemInfoReader.readMemInfo();
            long availMem = mMemInfoReader.getFreeSize()
                    + mMemInfoReader.getCachedSize() - mSecServerMem;
            long totalMem = mMemInfoReader.getTotalSize();

            String sizeStr = Formatter.formatShortFileSize(mContext, totalMem
                    - availMem);
            mForegroundProcessText.setText(mContext.getResources().getString(
                    R.string.service_foreground_processes, sizeStr));
            sizeStr = Formatter.formatShortFileSize(mContext, availMem);
            mBackgroundProcessText.setText(mContext.getResources().getString(
                    R.string.service_background_processes, sizeStr));

            float fTotalMem = totalMem;
            float fAvailMem = availMem;
            mRamUsageBar.setRatios((fTotalMem - fAvailMem) / fTotalMem, 0, 0);
        }
    };

    private Drawable getDefaultActivityIcon() {
        return mContext.getResources().getDrawable(R.drawable.ic_default);
    }

    private void updateFavorites() {
        final PackageManager pm = mContext.getPackageManager();
        List<String> validFavorites = new ArrayList<String>();
        mFavoriteIcons = new ArrayList<Drawable>();
        mFavoriteNames = new ArrayList<String>();
        Iterator<String> nextFavorite = mFavoriteList.iterator();
        while (nextFavorite.hasNext()) {
            String favorite = nextFavorite.next();
            Intent intent = null;
            Drawable appIcon = null;
            try {
                intent = Intent.parseUri(favorite, 0);
                appIcon = pm.getActivityIcon(intent);
            } catch (NameNotFoundException e) {
                Log.e(TAG, "NameNotFoundException: [" + favorite + "]");
                continue;
            } catch (URISyntaxException e) {
                Log.e(TAG, "URISyntaxException: [" + favorite + "]");
                continue;
            }
            validFavorites.add(favorite);
            String label = Utils.getActivityLabel(pm, intent);
            if (label == null) {
                label = favorite;
            }
            if (appIcon == null) {
                appIcon = getDefaultActivityIcon();
            }
            mFavoriteIcons.add(Utils.resize(mContext.getResources(),
                    appIcon, mConfiguration.mIconSize, 
                    mConfiguration.mDensity));
            mFavoriteNames.add(label);
        }
        mFavoriteList.clear();
        mFavoriteList.addAll(validFavorites);
    }
    
    public boolean isShowing() {
        return mShowing;
    }

    private boolean startShowcaseFavorite() {
        if (!mPrefs.getBoolean(KEY_SHOWCASE_FAVORITE, false)) {
            mPrefs.edit().putBoolean(KEY_SHOWCASE_FAVORITE, true).commit();
            ShowcaseView.ConfigOptions co = new ShowcaseView.ConfigOptions();
            co.hideOnClickOutside = true;

            Point size = new Point();
            mWindowManager.getDefaultDisplay().getSize(size);

            mShowcaseView = ShowcaseView.insertShowcaseView(size.x / 2, mOpenFavoriteY, mWindowManager, mContext,
                    R.string.sc_favorite_title, R.string.sc_favorite_body, co);

            mShowcaseView.animateGesture(size.x / 2, size.y * 2.0f / 3.0f,
                    size.x / 2, size.y / 2.0f);
            mShowcaseView.setOnShowcaseEventListener(this);
            mShowcaseDone = true;
            return true;
        }
        mShowcaseDone = true;
        return false;
    }
    @Override
    public void onShowcaseViewHide(ShowcaseView showcaseView) {
    }

    @Override
    public void onShowcaseViewShow(ShowcaseView showcaseView) {
    }
    
    public void updateLayout() {
        if (mShowing){
            mWindowManager.updateViewLayout(mPopupView, getParams());
        }
    }
}
