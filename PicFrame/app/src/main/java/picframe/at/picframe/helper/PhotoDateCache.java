package picframe.at.picframe.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import picframe.at.picframe.helper.viewpager.EXIF_helper;

/**
 * Cache for photo dates extracted from EXIF data.
 * This prevents repeatedly reading EXIF data which can cause UI freezing.
 */
public class PhotoDateCache {
    private static final String TAG = "PhotoDateCache";
    private static final String PREFS_NAME = "photo_date_cache";
    private static final boolean DEBUG = true;
    
    private static PhotoDateCache instance;
    private SharedPreferences prefs;
    private Map<String, Long> memoryCache;
    
    private PhotoDateCache(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        memoryCache = new HashMap<>();
        // Load all cached dates into memory for fast access
        loadCacheIntoMemory();
    }
    
    public static synchronized PhotoDateCache getInstance(Context context) {
        if (instance == null) {
            instance = new PhotoDateCache(context.getApplicationContext());
        }
        return instance;
    }
    
    private void loadCacheIntoMemory() {
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof Long) {
                memoryCache.put(entry.getKey(), (Long) entry.getValue());
            }
        }
        if (DEBUG) Log.d(TAG, "Loaded " + memoryCache.size() + " cached dates into memory");
    }
    
    /**
     * Get the cached date for a photo, or null if not cached.
     */
    public Date getCachedDate(String filePath) {
        Long timestamp = memoryCache.get(filePath);
        if (timestamp != null && timestamp > 0) {
            return new Date(timestamp);
        }
        return null;
    }
    
    /**
     * Get the date for a photo, reading from EXIF if not cached.
     * This should only be called from background threads.
     */
    public Date getDateAndCache(String filePath) {
        // Check cache first
        Long timestamp = memoryCache.get(filePath);
        if (timestamp != null) {
            return timestamp > 0 ? new Date(timestamp) : null;
        }
        
        // Not cached, read from EXIF
        Date date = EXIF_helper.getDateFromImage(filePath);
        
        // Cache the result (even if null, cache as -1 to avoid repeated reads)
        long timestampToCache = (date != null) ? date.getTime() : -1;
        memoryCache.put(filePath, timestampToCache);
        prefs.edit().putLong(filePath, timestampToCache).apply();
        
        return date;
    }
    
    /**
     * Update the cache for a list of files asynchronously.
     * This scans all files and caches their dates without blocking the UI.
     */
    public void updateCacheAsync(final List<String> filePaths, final CacheUpdateListener listener) {
        new AsyncTask<Void, Integer, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (DEBUG) Log.d(TAG, "Starting async cache update for " + filePaths.size() + " files");
                
                // First, remove any cached entries that are no longer in the file list
                pruneCacheForFileList(filePaths);
                
                // Then update cache for all files
                int updated = 0;
                for (int i = 0; i < filePaths.size(); i++) {
                    if (isCancelled()) break;
                    
                    String filePath = filePaths.get(i);
                    // Only read EXIF if not already cached
                    if (!memoryCache.containsKey(filePath)) {
                        getDateAndCache(filePath);
                        updated++;
                    }
                    
                    // Report progress every 10 files
                    if (i % 10 == 0) {
                        publishProgress(i, filePaths.size());
                    }
                }
                
                if (DEBUG) Log.d(TAG, "Cache update complete. Updated " + updated + " new files");
                return null;
            }
            
            @Override
            protected void onProgressUpdate(Integer... values) {
                if (listener != null) {
                    listener.onProgress(values[0], values[1]);
                }
            }
            
            @Override
            protected void onPostExecute(Void result) {
                if (listener != null) {
                    listener.onComplete();
                }
            }
        }.execute();
    }
    
    /**
     * Remove cached entries for files that no longer exist in the file list.
     */
    private void pruneCacheForFileList(List<String> currentFiles) {
        SharedPreferences.Editor editor = prefs.edit();
        int removed = 0;
        
        for (String cachedPath : memoryCache.keySet().toArray(new String[0])) {
            if (!currentFiles.contains(cachedPath)) {
                memoryCache.remove(cachedPath);
                editor.remove(cachedPath);
                removed++;
            }
        }
        
        if (removed > 0) {
            editor.apply();
            if (DEBUG) Log.d(TAG, "Pruned " + removed + " obsolete cache entries");
        }
    }
    
    /**
     * Clear the entire cache (useful for testing or troubleshooting).
     */
    public void clearCache() {
        memoryCache.clear();
        prefs.edit().clear().apply();
        if (DEBUG) Log.d(TAG, "Cache cleared");
    }
    
    /**
     * Interface for listening to cache update progress.
     */
    public interface CacheUpdateListener {
        void onProgress(int current, int total);
        void onComplete();
    }
}
