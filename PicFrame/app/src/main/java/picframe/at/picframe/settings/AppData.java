/*
    Copyright (C) 2015 Myra Fuchs, Linda Spindler, Clemens Hlawacek, Ebenezer Bonney Ussher

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

package picframe.at.picframe.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import picframe.at.picframe.activities._MainApp;
import picframe.at.picframe.R;
import picframe.at.picframe.helper.local_storage.SD_Card_Helper;

/**
 * Stores App Settings, to get and load easily
 * Created by ClemensH on 04.04.2015.
 */
public class AppData {
    public static final String mySettingsFilename = "PicFrameSettings";
    private static final SharedPreferences mPrefs = _MainApp.getINSTANCE().getSharedPreferences(mySettingsFilename, Context.MODE_PRIVATE);

    public static void resetSettings() {
        SettingsDefaults.resetSettings();
    }

    // enums for available source types (like images from SD-Card, OwnCloud or Dropbox)
    public enum sourceTypes {
        ExternalSD, OwnCloud, Dropbox;
        private static sourceTypes[] allValues = values();
        public static sourceTypes getSourceTypesForInt(int num){
            try{
                return allValues[num];
            }catch(ArrayIndexOutOfBoundsException e){
                return ExternalSD;
            }
        }
    }

    /* TODO
    extFolderAppRoot = new SD_Card_Helper().getExteralStoragePath() +
                File.separator + "picframe";
        extFolderDisplayPath = extFolderAppRoot + File.separator + "pictures";
        extFolderCachePath = extFolderAppRoot + File.separator + "cache";
    *
    * */

// ONLY TO BE MODIFIED BY SETTINGS ACTIVITY
    // flag whether slideshow is selected(on=true) or not(off=false)
    public static boolean getSlideshow() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_slideshow),
                (Boolean) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_slideshow));
    }

    // holds the time to display each picture in seconds
    public static int getDisplayTime() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_displaytime),
                (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_displaytime)));
    }

    // holds the int of the transitionStyle - @res.values.arrays.transitionTypeValues
    public static int getTransitionStyle(){
        return Integer.parseInt(
                mPrefs.getString(getAppContext().getString(R.string.sett_key_transition),
                        (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_transition)));
    }

    // Sort order for displayed images (0=filename, 1=random, 2=photo date)
    public static int getSortOrder() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_sortOrder),
                (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_sortOrder)));
    }

    // flag whether to randomize the order of the displayed images (on=true)
    // Deprecated: Use getSortOrder() instead
    public static boolean getRandomize() {
        return getSortOrder() == 1;
    }

    // flag whether to scale the displayed image (on=true)
    public static boolean getScaling() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_scaling),
                (Boolean) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_scaling));
    }

    // flag whether to show date/time overlay (on=true)
    public static boolean getOverlayEnabled() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_overlay_enabled), false);
    }

    // holds the format of the date/time overlay (0=year, 1=year+month, 2=full date, 3=date+time, 4=time only)
    public static int getOverlayFormat() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_overlay_format), "2"));
    }

    // holds the position of the overlay (0=top-left, 1=top-center, 2=top-right, 3=bottom-left, 4=bottom-center, 5=bottom-right)
    public static int getOverlayPosition() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_overlay_position), "5"));
    }

    // holds the size of the overlay text (0=small, 1=medium, 2=large, 3=extra large)
    public static int getOverlaySize() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_overlay_size), "1"));
    }

    // holds the style of the overlay text (0=white, 1=black, 2=white with black outline, 3=black with white outline)
    public static int getOverlayStyle() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_overlay_style), "2"));
    }

    // flag whether to show location overlay (on=true)
    public static boolean getLocationEnabled() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_location_enabled), false);
    }

    // holds the format of the location overlay (0=city and country, 1=city only)
    public static int getLocationFormat() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_location_format), "0"));
    }

    // holds the position of the location overlay (0=top-left, 1=top-center, 2=top-right, 3=bottom-left, 4=bottom-center, 5=bottom-right)
    public static int getLocationPosition() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_location_position), "3"));
    }

    // flag whether kiosk mode is enabled (on=true)
    public static boolean getKioskEnabled() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_kiosk_enabled), false);
    }

    // holds the kiosk mode password
    public static String getKioskPassword() {
        return mPrefs.getString(getAppContext().getString(R.string.sett_key_kiosk_password), "");
    }

    // sets the kiosk mode enabled state
    public static void setKioskEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(getAppContext().getString(R.string.sett_key_kiosk_enabled), enabled).apply();
    }

    // holds the type of the selected source as int
    public static int getSrcTypeInt() {
        return Integer.parseInt(
                mPrefs.getString(getAppContext().getString(R.string.sett_key_srctype),
                        (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_srctype)));
    }
    // holds the type of the selected source
    public static sourceTypes getSourceType() {
        return sourceTypes.getSourceTypesForInt(Integer.parseInt(
                mPrefs.getString(getAppContext().getString(R.string.sett_key_srctype),
                        (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_srctype))));
    }

    // holds the username to log into the owncloud account
    public static String getUserName() {
        return mPrefs.getString(getAppContext().getString(R.string.sett_key_username),
                (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_username));
    }

    // holds the password to log into the owncloud account
    public static String getUserPassword() {
        return mPrefs.getString(getAppContext().getString(R.string.sett_key_password),
                (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_password));
    }

    // Returns selected SD-Card directory, or URL to owncloud or samba server
    // holds the path to the image source (from where to (down)-load them
    public static String getSourcePath() {
        sourceTypes tmpType = getSourceType();
        if (sourceTypes.OwnCloud.equals(tmpType)) {
            return mPrefs.getString(getAppContext().getString(R.string.sett_key_srcpath_owncloud),
                    (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_srcpath_dropbox));
        } else if (sourceTypes.Dropbox.equals(tmpType)) {
            return mPrefs.getString(getAppContext().getString(R.string.sett_key_srcpath_dropbox), "");
        } else {    // if SD or undefined, get SD path
            return mPrefs.getString(getAppContext().getString(R.string.sett_key_srcpath_sd),
                    (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_srcpath_sd));
        }
    }

    // holds the time-interval to initiate the next download of images in hours
    public static int getUpdateIntervalInHours() {
        return Integer.parseInt(mPrefs.getString(getAppContext().getString(R.string.sett_key_downloadInterval),
               (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_downloadInterval)));
    }


    // flag whether to include images in subfolders (on=true)
    public static boolean getRecursiveSearch() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_recursiveSearch),
                (Boolean) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_recursiveSearch));
    }

    // Always returns the path to the img folder of current src type
    // holds the root-path to the displayed images
    public static String getImagePath() {
        sourceTypes tmpType = getSourceType();
        if (sourceTypes.ExternalSD.equals(tmpType)) {
            return mPrefs.getString(getAppContext().getString(R.string.sett_key_srcpath_sd),
                    (String) SettingsDefaults.getDefaultValueForKey(R.string.sett_key_srcpath_sd));
        } else {
            return getExtFolderDisplayPath();
        }
    }

// CAN ALWAYS BE MODIFIED
/*
    public static String getSdSourcePath() {
        return mPrefs.getString(getAppContext().getString(R.string.sett_key_srcpath_sd), "");
    }
*/
    // get flag whether the last loginCheck for a server was successful or not
    public static boolean getLoginSuccessful() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_loginCheckButton), false);
    }
    // set flag whether the last loginCheck for a server is successful or not
    public static void setLoginSuccessful(boolean loginSuccess) {
        mPrefs.edit().putBoolean(getAppContext().getString(R.string.sett_key_loginCheckButton), loginSuccess).commit();
    }

    public static void setSdSourcePath(String path) {
        mPrefs.edit().putString(getAppContext().getString(R.string.sett_key_srcpath_sd), path).commit();
    }

    // flag whether this is the first app start
    public static boolean getFirstAppStart() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_firstStart), true);
    }
    public static void setFirstAppStart(boolean firstAppStart) {
        mPrefs.edit().putBoolean(getAppContext().getString(R.string.sett_key_firstStart), firstAppStart).commit();
    }

    // flag whether to display the tutorial-dialog (on=true)
    public static boolean getTutorial() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_tutorial), true);
    }
    public static void setTutorial(boolean showTutorial) {
        mPrefs.edit().putBoolean(getAppContext().getString(R.string.sett_key_tutorial), showTutorial).commit();
    }

    public static int getCurrentPage(){
        return mPrefs.getInt(getAppContext().getString(R.string.sett_key_currentPage), 1);
    }

    public static void setCurrentPage(int page){
                mPrefs.edit().putInt(getAppContext().getString(R.string.sett_key_currentPage), page).commit();
    }

    // flag for slideshow direction
    public static boolean getDirection() {
        return mPrefs.getBoolean(getAppContext().getString(R.string.sett_key_direction), true);
    }
    public static void setDirection(boolean toggleDirection) {
        mPrefs.edit().putBoolean(getAppContext().getString(R.string.sett_key_direction), toggleDirection).commit();
    }

    // last alarm time in millisecs
    public static Long getLastAlarmTime() {
        return mPrefs.getLong(getAppContext().getString(R.string.sett_key_lastAlarmTime), -1);
    }

    public static void setLastAlarmTime (Long time) {
        mPrefs.edit().putLong(getAppContext().getString(R.string.sett_key_lastAlarmTime), time).commit();
    }

    public static Long getNextAlarmTime() {
        return mPrefs.getLong(getAppContext().getString(R.string.sett_key_nextAlarmTime), -1);
    }

    public static void setNextAlarmTime (Long time) {
        mPrefs.edit().putLong(getAppContext().getString(R.string.sett_key_nextAlarmTime), time).commit();
    }
/* TODO
    // holds the remaining time to display the current image
    public static int getRemainingDisplayTime() {
        return mPrefs.getInt(getAppContext().getString(R.string.sett_key_), 0);
    }
    public static void setRemainingDisplayTime(int remainingDisplayTime) {
        mPrefs.edit().putInt(getAppContext().getString(R.string.sett_key_), remainingDisplayTime).commit();
    }
*/

// CAN NEVER BE MODIFIED!   (holds local paths, desc at vars)
    // sd-card-dir/picframe
    public static String getExtFolderAppRoot() {
        return new SD_Card_Helper().getExteralStoragePath() + File.separator + "picframe";
    }
    // sd-card-dir/picframe/cache
    public static String getExtFolderCachePath() {
        return (getExtFolderAppRoot() + File.separator + "cache");
    }
    // sd-card-dir/picframe/pictures
    public static String getExtFolderDisplayPath() {
        return (getExtFolderAppRoot() + File.separator + "pictures");
    }

    public static SharedPreferences getSharedPreferences() {
        return mPrefs;
    }
    private static Context getAppContext() {
        return _MainApp.getINSTANCE().getApplicationContext();
    }
}