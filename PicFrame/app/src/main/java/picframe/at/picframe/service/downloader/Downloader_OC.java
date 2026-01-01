package picframe.at.picframe.service.downloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.os.Handler;
import android.util.Log;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.network.OnDatatransferProgressListener;
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.ReadRemoteFolderOperation;
import com.owncloud.android.lib.resources.files.RemoteFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import picframe.at.picframe.helper.GlobalPhoneFuncs;
import picframe.at.picframe.helper.Keys;
import picframe.at.picframe.helper.viewpager.EXIF_helper;
import picframe.at.picframe.service.ServiceCallbacks;


@SuppressWarnings("ConstantConditions")
public class Downloader_OC extends Downloader implements OnRemoteOperationListener, OnDatatransferProgressListener {
    private final String TAG = this.getClass().getSimpleName();
    private final boolean DEBUG = true;

    // Keys for the argument hashmap
    public static final String CLIENT = "OcClient";
    public static final String HANDLER = "OcHandler";
    public static final String REMOTEFOLDER = "OcRemoteFolder";

    // private fields holding the download-parameter-data
    private OwnCloudClient mClient;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private String remoteFolder;                                // folder, in which the download should start   TODO folderpicker stuff
    private Handler mHandler;
    private Context mContext;                                   // only needed for EXIF-helper to get screen width and height
    private String mExtFolderAppRoot;
    private String mExtFolderDisplayPath;
    private String mExtFolderCachePath;
    private ArrayList<String> mLocalFileList;                   // files (relative path), that are currently in the display folder
    private ArrayList<RemoteFile> mRemoteFileList;              // all files on remote server
    private ArrayList<RemoteFile> mRemoteFilesToDownloadList;   // files that need to be downloaded (full remote paths to the files)
    public LinkedBlockingQueue<String> mDownloadedFiles;        // files downloaded successfully - empty after processed all files
    private AtomicInteger mDownloadedFilesCount;                // total count of successfully downloaded files
    private AtomicInteger mThreadCounter;                       // Count active threads so task only finishes once all threads are done



    private boolean failedOnce = false;                         // only relay the first failure to the service (flag to ensure this)
    private final int MIN_MB_REMAINING = 15;                    // if storage is smaller than 15 MB, stop the download!
    private final int MIN_BYTES_REMAINING = MIN_MB_REMAINING * 1024 * 1024;
    private AtomicBoolean loginRequest = new AtomicBoolean(false);
    private AtomicBoolean loginResultSuccess = new AtomicBoolean(false);



    private boolean loginFailed = false;
    private boolean firstOperation = true;

    private String currentFileName = null;

    public Downloader_OC(HashMap<String, Object> args) {
        if (DEBUG) Log.d(TAG, "created OC Downloader");
        if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: created OC Downloader");
        if (args == null)   return;
        mClient = (OwnCloudClient) args.get(CLIENT);
        mHandler = (Handler) args.get(HANDLER);
        mExtFolderAppRoot = (String) args.get(Keys.PICFRAMEPATH);
        remoteFolder = (String) args.get(REMOTEFOLDER);
        serviceCallbacks = (ServiceCallbacks) args.get(Keys.CALLBACK);
        mContext = (Context) args.get(Keys.CONTEXT);
    }

    @Override
    public void run() {
        if (DEBUG) Log.d(TAG, "started OC Downloader");
        if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: started OC Downloader");
        initializeCounterArraysQueuesPaths();

        if (DEBUG) Log.i(TAG,
                    "AppRootFolder: "   + mExtFolderAppRoot +
                " -- CacheFolder: "     + mExtFolderCachePath +
                " -- DisplayFoler: "    + mExtFolderDisplayPath);
        if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: AppRootFolder: " + mExtFolderAppRoot + " -- CacheFolder: " + mExtFolderCachePath + " -- DisplayFolder: " + mExtFolderDisplayPath);
        if (DEBUG) Log.i(TAG, "thread counter at first: " + mThreadCounter.get());
        if (DEBUG) Log.i(TAG, "###### STARTING OWNCLOUD DOWNLOAD TASK #####");


        if (!checkLogin()) {
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Login failed");
            downloadFailure(Failure.LOGIN);
            return;
        }
        // populate the mRemoteFileList with all the (accepted) remote files found on server
        if (!readAllRemoteFoldersInDirAndWait()) {
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Failed to read remote folders");
            downloadFailure(Failure.INTERRUPT);
            return;
        }
        // check which files have to be downloaded (not downloaded yet) (false if none/0 to download)
        if (!compareListsAndCheckForNewFilesToDownload()) {
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: No new files to download");
            finishedDownload();
            return;
        }
        // process and download all images (false if interrupted)
        if (!processAndDownloadLoop()) {
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Download loop interrupted");
            downloadFailure(Failure.INTERRUPT);
            return;
        }

        if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Finished download task");
        finishedDownload();
        // END OF THREAD!
    }

    private void initializeCounterArraysQueuesPaths() {
        // set counter to zero
        mThreadCounter = new AtomicInteger(0);
        mDownloadedFilesCount = new AtomicInteger(0);

        // initialise empty arraylists and queue
        mRemoteFileList = new ArrayList<>();
        mLocalFileList = new ArrayList<>();
        mRemoteFilesToDownloadList = new ArrayList<>();
        mDownloadedFiles = new LinkedBlockingQueue<>();

        // build the paths to the needed folders    // TODO: once new AppData is merged, change these 2 lines
        mExtFolderDisplayPath = mExtFolderAppRoot + File.separator + "pictures";
        mExtFolderCachePath = mExtFolderAppRoot + File.separator + "cache";
    }

    private boolean checkLogin() {
        loginRequest.set(true);
        mThreadCounter.getAndIncrement();
        ReadRemoteFolderOperation loginCheckOperation =
                new ReadRemoteFolderOperation(FileUtils.PATH_SEPARATOR);
        loginCheckOperation.execute(mClient, this, mHandler);
        while (loginRequest.get()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return loginResultSuccess.get();
    }

    private boolean readAllRemoteFoldersInDirAndWait() {
        // read all files on the server                     // TODO: instead of "/" use remoteFolderPath
        remoteReadAnyFolder(FileUtils.PATH_SEPARATOR);      // TODO: limit read too (like download)
        // wait for all read operations to finish
        while(mThreadCounter.get() > 0) {
            if (isInterrupted()) {
                if (DEBUG) Log.e(TAG, "Thread cancelled! -- position: run() - waited for reads to end");
                return false;
            }
        }
        return true;
    }

    private void remoteReadAnyFolder(String path) {
        if (isInterrupted()) {
            if (DEBUG) Log.e(TAG, "Thread cancelled! -- position: remoteReadAnyFolder");
            downloadFailure(Failure.INTERRUPT);
            return;
        }
        mThreadCounter.getAndIncrement();
        ReadRemoteFolderOperation refreshOperation = new ReadRemoteFolderOperation(path);
        refreshOperation.execute(mClient, this, mHandler);
    }

    private boolean compareListsAndCheckForNewFilesToDownload(){
        File mExtFolderCacheObject = new File(mExtFolderDisplayPath);
        File[] mLocalFilesInDisplayFolder = mExtFolderCacheObject.listFiles();

        // Build a set of remote file paths for efficient lookup
        ArrayList<String> mRemoteFilePaths = new ArrayList<>();
        for (RemoteFile remoteFile : mRemoteFileList) {
            mRemoteFilePaths.add(remoteFile.getRemotePath());
        }

        if (mLocalFilesInDisplayFolder == null || mLocalFilesInDisplayFolder.length == 0) {
            if (DEBUG) Log.i(TAG, "Display folder is empty, all remote files are to be downloaded.");
            for(RemoteFile remoteFile : mRemoteFileList){
                mRemoteFilesToDownloadList.add(remoteFile);
            }
        } else {
            if (DEBUG) Log.i(TAG, "Display folder not empty, checking files...");
            // save names (with relative path) of all files in local folder
            addFilepathsToLocalFileList(mLocalFilesInDisplayFolder);
            // for every remote file, check if it already exists locally
            for (RemoteFile remoteFile : mRemoteFileList) {
                if (mLocalFileList.contains(remoteFile.getRemotePath())) {
                    if (DEBUG) Log.i(TAG, "File(" + remoteFile.getRemotePath() + ") exists locally!");
                } else {
                    // if the file is new, save its path
                    if (DEBUG) Log.i(TAG, "File(" + remoteFile.getRemotePath() + ") doesn't exist locally! -> download it!");
                    mRemoteFilesToDownloadList.add(remoteFile);
                }
            }
            
            // Delete local files that no longer exist on the server
            deleteOrphanedLocalFiles(mRemoteFilePaths);
        }
        if (mRemoteFilesToDownloadList.size() == 0) {
            if (DEBUG) Log.i(TAG, "No file needs to be downloaded!");
            return false;
        } else {
            Collections.sort(mRemoteFilesToDownloadList, new Comparator<RemoteFile>() {
                @Override
                public int compare(RemoteFile lhs, RemoteFile rhs) {
                    return lhs.getRemotePath().compareToIgnoreCase(rhs.getRemotePath());
                }
            });
            if (DEBUG) Log.i(TAG, "Starting download of missing files (" + mRemoteFilesToDownloadList.size() + ") shortly");
            return true;
        }
    }

    /**
     * Deletes local files that no longer exist on the remote server.
     * This ensures that when photos are deleted on the server, they are also
     * removed from the local cache during the next sync.
     *
     * @param remoteFilePaths List of file paths that exist on the remote server
     */
    private void deleteOrphanedLocalFiles(ArrayList<String> remoteFilePaths) {
        int deletedCount = 0;
        for (String localFilePath : mLocalFileList) {
            if (!remoteFilePaths.contains(localFilePath)) {
                // This local file doesn't exist on the server anymore - delete it
                File fileToDelete = new File(mExtFolderDisplayPath + localFilePath);
                if (fileToDelete.exists() && fileToDelete.isFile()) {
                    boolean deleted = fileToDelete.delete();
                    if (deleted) {
                        deletedCount++;
                        if (DEBUG) Log.i(TAG, "Deleted orphaned local file: " + localFilePath);
                    } else {
                        if (DEBUG) Log.e(TAG, "Failed to delete orphaned local file: " + localFilePath);
                    }
                }
            }
        }
        if (deletedCount > 0) {
            if (DEBUG) Log.i(TAG, "Deleted " + deletedCount + " orphaned local file(s) that were removed from server");
        }
        
        // Also clean up empty directories
        cleanupEmptyDirectories(new File(mExtFolderDisplayPath));
    }

    /**
     * Recursively removes empty directories from the display folder.
     * This is called after deleting orphaned files to clean up any
     * empty folder structures that may remain.
     *
     * @param directory The directory to check and clean
     * @return true if the directory was deleted (was empty), false otherwise
     */
    private boolean cleanupEmptyDirectories(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }
        
        // First, recursively clean subdirectories
        for (File file : files) {
            if (file.isDirectory()) {
                cleanupEmptyDirectories(file);
            }
        }
        
        // After cleaning subdirectories, check if this directory is now empty
        files = directory.listFiles();
        if (files != null && files.length == 0) {
            // Don't delete the root display folder itself
            if (!directory.getPath().equals(mExtFolderDisplayPath)) {
                boolean deleted = directory.delete();
                if (deleted && DEBUG) {
                    Log.i(TAG, "Deleted empty directory: " + directory.getPath());
                }
                return deleted;
            }
        }
        return false;
    }

    private void addFilepathsToLocalFileList(File[] files){
        for (File file : files) {
            if (file.isDirectory()) {
                addFilepathsToLocalFileList(file.listFiles());
            } else {
                // substring to get the relative path
                String fileRelPath = file.getPath().substring(mExtFolderDisplayPath.length());
                //if (DEBUG) Log.i(TAG + " -addFilepaths", "filename:" + fileRelPath);
                mLocalFileList.add(fileRelPath);
            }
        }
    }

    private boolean processAndDownloadLoop() {
        int fileIndex = 0;
        File mExtFolderCacheObject = new File(mExtFolderCachePath);
        boolean cacheFolderExists = mExtFolderCacheObject.exists();
        while( mThreadCounter.get() > 0 ||
            (mDownloadedFilesCount.get() != mRemoteFilesToDownloadList.size()) ||
            mDownloadedFiles.peek() != null) {
            if (mDownloadedFiles.peek() != null) {
                if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Processing file " + mDownloadedFiles.peek());
                //scale, rotate, save smaller image, delete cached/big image
                processFile();
            }
            if (isInterrupted()) {
            // if the user aborted the download, process the remaining files
                if (DEBUG) Log.e(TAG, "Thread cancelled! -- position: while-loop in 'run()'");
                if (mDownloadedFiles.peek() == null) {
                    return false;
                }
            } else {
            // else carry on downloading the remaining images
                if (mRemoteFilesToDownloadList.size() > 0 &&
                        mDownloadedFilesCount.get() < mRemoteFilesToDownloadList.size()) {
                    // check every 3 files, if still connected to wifi
                    if ((fileIndex % 3) == 0) {
                        if (!GlobalPhoneFuncs.wifiConnected()) {
                            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Lost WiFi connection during download");
                            downloadFailure(Failure.WIFI);
                            continue;
                        }
                    }
                    // start download
                    if (mThreadCounter.get() < 2 && fileIndex < mRemoteFilesToDownloadList.size()) {
                        // Ensure cache directory exists before each download
                        if (!mExtFolderCacheObject.exists()) {
                            boolean created = mExtFolderCacheObject.mkdirs();
                            if (created) {
                                if (DEBUG) Log.i(TAG, "Created cache directory: " + mExtFolderCacheObject.getAbsolutePath());
                                if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Created cache directory: " + mExtFolderCacheObject.getAbsolutePath());
                            } else {
                                if (DEBUG) Log.e(TAG, "FAILED to create cache directory: " + mExtFolderCacheObject.getAbsolutePath());
                                if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: FAILED to create cache directory: " + mExtFolderCacheObject.getAbsolutePath());
                            }
                        } else {
                            if (DEBUG) Log.i(TAG, "Cache directory already exists: " + mExtFolderCacheObject.getAbsolutePath());
                            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Cache directory already exists: " + mExtFolderCacheObject.getAbsolutePath());
                        }
                        RemoteFile remoteFile = mRemoteFilesToDownloadList.get(fileIndex);

						// Log remote file info and path
						if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Remote file to download: " + remoteFile.toString());
						if (DEBUG) Log.i(TAG, "Remote file to download: " + remoteFile.toString());
						if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Downloading remote path: " + remoteFile.getRemotePath());
						if (DEBUG) Log.i(TAG, "Downloading remote path: " + remoteFile.getRemotePath());

                        // if more than MIN_BYTES_REMAINING bytes would be free after the download -> download
                        if ((GlobalPhoneFuncs.getSdCardFreeBytes() - remoteFile.getLength()) > MIN_BYTES_REMAINING) {
                            if (DEBUG) Log.i(TAG, "Preparing to start download: " + remoteFile.getRemotePath());
                            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Preparing to start download: " + remoteFile.getRemotePath());
                            mThreadCounter.getAndIncrement();
                            DownloadRemoteFileOperation downloadOperation =
                                    new DownloadRemoteFileOperation(remoteFile.getRemotePath(), mExtFolderCacheObject.getAbsolutePath());
                            if (DEBUG) Log.i(TAG, "Calling downloadOperation.execute for: " + remoteFile.getRemotePath());
                            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Calling downloadOperation.execute for: " + remoteFile.getRemotePath());
                            downloadOperation.execute( mClient, this, mHandler);
                            if (DEBUG) Log.i(TAG, "Returned from downloadOperation.execute for: " + remoteFile.getRemotePath());
                            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Returned from downloadOperation.execute for: " + remoteFile.getRemotePath());
                            fileIndex++;
                        } else {
                            if (DEBUG) Log.e(TAG, "No more space available on the sd card!");
                            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: No more space available on the sd card!");
                            downloadFailure(Failure.STORAGESPACE);
                        }
                    }
                }
            }
        }
        return true;
    }

    private void processFile() {
        boolean processed;
        boolean deleted;
        String fileToProcess = mDownloadedFiles.poll();
        processed = scaleRotateAndSave(fileToProcess);
        if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: File processed: " + fileToProcess + ", success: " + processed);
        if (processed) {    // if rotate, scale and move worked, delete from list, and delete src file
            if (DEBUG) Log.i(TAG, "rot/scaled/saved >" + fileToProcess + "<");
            File fileToDelete = new File(fileToProcess);
            if (fileToDelete.exists() && fileToDelete.isFile()) {
                deleted = fileToDelete.delete();
                if (deleted)
                    if (DEBUG) Log.i(TAG, "deleted >" + fileToProcess + "<");
                else
                    if (DEBUG) Log.e(TAG, "couldn't delete >" + fileToProcess + "<");
            }
        } else {
            if (DEBUG) Log.e(TAG, "Failure while processing file[" + fileToProcess + "]. (scaleRotateAndSave");
        }
        publishProgress((float)mDownloadedFilesCount.get() / (float)mRemoteFilesToDownloadList.size(), false);  // is between 0 and 1
    }

    private boolean scaleRotateAndSave(String filepath) {
        if (filepath.contains(mExtFolderCachePath)) {
            filepath = filepath.substring(mExtFolderCachePath.length());
        }
        String srcPath = mExtFolderCachePath + filepath;
        if (DEBUG) Log.i(TAG, "srcPath: " + srcPath);
        String newPath = mExtFolderDisplayPath + filepath;
        if (DEBUG) Log.i(TAG, "newPath: " + newPath);

        Bitmap image = EXIF_helper.decodeFile(srcPath, mContext);
        String fs = File.separator;
        boolean dirCreationWorked;
        if (filepath.startsWith(File.separator)) {
            filepath = filepath.substring(1, filepath.length());
        }
        String[] parts = filepath.split(Pattern.quote(fs));
        if(filepath.contains(fs)) {
            String pathToCreate = mExtFolderDisplayPath;
            for(int i = 0; i < parts.length-1; i++) {
                if (!pathToCreate.endsWith(File.separator))
                    pathToCreate = pathToCreate.concat(File.separator);
                pathToCreate = pathToCreate.concat(parts[i]);
                File dirMaking =  new File(pathToCreate);
                if (dirMaking.exists() && dirMaking.isDirectory())
                    continue;
                dirCreationWorked = dirMaking.mkdirs();
                if (!dirCreationWorked) {
                    if (DEBUG) Log.i(TAG, "FAILED to create: " + dirMaking);
                    return false;
                }
            }
        }
        File f = new File(newPath);
        // Check whether scaled img exists
        if(f.exists()) {
            if (DEBUG) Log.i(TAG, "testfile exists - deleting now");
            if (f.delete()) {
                if (DEBUG) Log.i(TAG, "deletion successfull");
            } else {
                if (DEBUG) Log.e(TAG, "deletion failed");
            }
        }
        if (DEBUG) Log.i(TAG, "Saving...  Filepath: " + f.getAbsolutePath());
        FileOutputStream fOut;
        try {
            fOut  = new FileOutputStream(f);
            if(parts[parts.length-1].toLowerCase().endsWith(".jpg") ||
                    parts[parts.length-1].toLowerCase().endsWith(".jpeg")) {
                image.compress(Bitmap.CompressFormat.JPEG, 80, fOut);
            }
            else {
                image.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            }
            fOut.flush();
            fOut.close();
            
            // Copy EXIF GPS data from source to destination (only for JPEG)
            if(parts[parts.length-1].toLowerCase().endsWith(".jpg") ||
                    parts[parts.length-1].toLowerCase().endsWith(".jpeg")) {
                copyExifGpsData(srcPath, newPath);
            }
        } catch (NullPointerException npe) {
            if (DEBUG) Log.e(TAG, "Couldn't compress bitmap");
            //npe.printStackTrace();
            return false;
        } catch (FileNotFoundException e) {
            if (DEBUG) Log.e(TAG, "Couldn't create FileOutputStream - file not found");
            //e.printStackTrace();
            return false;
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Couldn't flush or close");
            //e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Copies EXIF GPS and date/time data from source image to destination image.
     * This preserves location and timestamp information after bitmap compression.
     */
    private void copyExifGpsData(String srcPath, String destPath) {
        try {
            ExifInterface srcExif = new ExifInterface(srcPath);
            ExifInterface destExif = new ExifInterface(destPath);
            
            // Copy GPS-related tags
            String[] gpsTagsToCopy = {
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED
            };
            
            for (String tag : gpsTagsToCopy) {
                String value = srcExif.getAttribute(tag);
                if (value != null) {
                    destExif.setAttribute(tag, value);
                }
            }
            
            destExif.saveAttributes();
            if (DEBUG) Log.i(TAG, "Copied EXIF GPS data from " + srcPath + " to " + destPath);
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Failed to copy EXIF GPS data: " + e.getMessage());
        }
    }

    @Override
    public void onRemoteOperationFinish(RemoteOperation operation, RemoteOperationResult result) {
        // Log entry to callback
        if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: ENTERED onRemoteOperationFinish callback: operation=" + operation.getClass().getSimpleName());
        if (DEBUG) Log.i(TAG, "ENTERED onRemoteOperationFinish callback");
        // Log operation type and result
        if (loginRequest.get()) {
            if (operation instanceof ReadRemoteFolderOperation) {
                if (result.isSuccess()) {
                    if (DEBUG)  Log.d(TAG, "operation successful, login/connection success");
                    loginResultSuccess.set(true);
                } else {
                    if (DEBUG)  Log.d(TAG, "operation unsuccessful, login/connection failed");
                }
            }
            mThreadCounter.getAndDecrement();
            // Log the completion of the login operation
            loginRequest.set(false);
            return;
        }
        if (operation instanceof ReadRemoteFolderOperation) {
            handleReadOperation(result);
        } else if (operation instanceof DownloadRemoteFileOperation) {
            // Log the download operation result
            // Use only available info from result, fix double-prepending of cache dir
            String fileName = result.getFilename();
            String fullLocalPath;
            if (fileName != null && fileName.startsWith(mExtFolderCachePath)) {
                fullLocalPath = fileName;
            } else {
                fullLocalPath = mExtFolderCachePath + File.separator + fileName;
            }
            File localFile = new File(fullLocalPath);
            boolean exists = localFile.exists();
            File parentDir = localFile.getParentFile();
            boolean parentExists = parentDir != null && parentDir.exists();
            boolean parentWritable = parentDir != null && parentDir.canWrite();
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Downloaded file should be at: " + fullLocalPath + ", exists: " + exists + ", parent dir exists: " + parentExists + ", parent dir writable: " + parentWritable);
            if (DEBUG) Log.i(TAG, "Downloaded file should be at: " + fullLocalPath + ", exists: " + exists + ", parent dir exists: " + parentExists + ", parent dir writable: " + parentWritable);
            if (!exists) {
                if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: ERROR - Local file does not exist after download: " + fullLocalPath);
                if (DEBUG) Log.e(TAG, "ERROR - Local file does not exist after download: " + fullLocalPath);
            }
            // Log HTTP status and exception
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Download result HTTP code: " + result.getHttpCode() + ", code: " + result.getCode() + ", exception: " + (result.getException() != null ? result.getException().toString() : "none"));
            if (DEBUG) Log.i(TAG, "Download result HTTP code: " + result.getHttpCode() + ", code: " + result.getCode() + ", exception: " + (result.getException() != null ? result.getException().toString() : "none"));
            handleDownloadOperation(result);
            if (result.isSuccess()) {
                if (DEBUG) Log.i(TAG, "onRemoteOperationFinish: Download complete for " + result.getFilename());
                if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Download complete for " + result.getFilename());
            } else {
                if (DEBUG) Log.e(TAG, "onRemoteOperationFinish: Download failed for " + result.getFilename() + ", reason: " + result.getLogMessage());
                // Log the failure reason
                if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Download failed for " + result.getFilename() + ", reason: " + result.getLogMessage());
            }
        }
    }

    private void handleReadOperation(RemoteOperationResult result) {
        if (result.isSuccess()) {
            ArrayList files = (ArrayList)result.getData();
            for(int i=0; i < files.size(); i++){
                if (i==0) continue;     // don't include first element in list as that is the read folder itself
                RemoteFile remoteFile = (RemoteFile) files.get(i);
                String mimeType = remoteFile.getMimeType();
                switch (mimeType) {
                    case "DIR":
                        // Skip Synology @eaDir metadata folders
                        String remotePath = remoteFile.getRemotePath();
                        if (remotePath.contains("@eaDir")) {
                            if (DEBUG) Log.i(TAG, "Skipping Synology metadata folder: " + remotePath);
                            break;
                        }
                        if (DEBUG) Log.i(TAG, "remote folder[" + mimeType + "]: " + remotePath);
                        remoteReadAnyFolder(remotePath);
                        break;
                    case "image/jpeg":
                    case "image/png":
                        if (DEBUG) Log.i(TAG, "remote file[" + mimeType + "]: " + remoteFile.getRemotePath());
                        if (!mRemoteFileList.contains(remoteFile)) {
                            mRemoteFileList.add(remoteFile);
                        }
                        break;
                    default:
                        if (DEBUG) Log.i(TAG, "WRONG file[" + mimeType + "]: " + remoteFile.getRemotePath());
                        break;
                }
            }
        }
        mThreadCounter.getAndDecrement();
        if (DEBUG) Log.i(TAG, "Threadcounter after read operation: " + mThreadCounter.get());
    }

    private void handleDownloadOperation(RemoteOperationResult result) {
        // Set current file name for progress reporting
        currentFileName = result.getFilename();
        if (result.isSuccess()) {
            if (DEBUG) Log.d(TAG, "Download: success -- info:" + result.getFilename()); // LogMessage: success, status code 200
            try {
                mDownloadedFiles.put(result.getFilename());
            } catch (InterruptedException e) {
                if (DEBUG) Log.e(TAG, "Couldn't put the downloaded file (" + result.getFilename() + ") on the list of downloaded files");
                e.printStackTrace();
            }
            mDownloadedFilesCount.getAndIncrement();
            // publishProgress was here before
        } else {
            if (DEBUG) Log.e(TAG, "Download: FAILURE -- info:" + result.getFilename() + " = " + result.getLogMessage());
        }
        mThreadCounter.getAndDecrement();
        
    }

	@Override
	public String getCurrentFileName() {
		return currentFileName;
	}
    
	@Override
    public void onTransferProgress(long progressRate, long totalTransferredSoFar,
                                   long totalToTransfer, String fileAbsoluteName) { }


    /*  CALLBACKS TO SERVICE */

    private void publishProgress(float percentAsFloat, boolean indeterminate) {
        if (serviceCallbacks != null) {
            if (percentAsFloat > 0)
                percentAsFloat *= 100;
            serviceCallbacks.publishProgress(percentAsFloat, indeterminate);
            serviceCallbacks.publishDebugLog("Downloader_OC: Progress update: " + percentAsFloat + "%");
            if (DEBUG) Log.i(TAG, "% update: " + percentAsFloat);
        } else {
            if (DEBUG)  Log.d(TAG, "couldn't publish progress, event object is null");
        }
    }

    private void finishedDownload() {
        if (serviceCallbacks != null) {
            if (DEBUG) Log.i(TAG, "###### FINISHED OWNCLOUD TASK #####");
            serviceCallbacks.publishDebugLog("Downloader_OC: Finished download task, total files: " + mDownloadedFilesCount.get());
            serviceCallbacks.finishedDownload(mDownloadedFilesCount.get());
        } else {
            if (DEBUG)  Log.d(TAG, "couldn't 'finish' download, event obj.is null");
        }
    }

    private void downloadFailure(Downloader.Failure failType) {
        if (!failedOnce) {
            // Only send the first failureType to the Service (could be Wifi->Interrupt)
            failedOnce = true;
            Thread.currentThread().interrupt();
            if (DEBUG) Log.e(TAG, "Thread interrupted!");
            if (serviceCallbacks != null) serviceCallbacks.publishDebugLog("Downloader_OC: Download failed: " + failType);
            if (serviceCallbacks != null) {
                serviceCallbacks.downloadFailed(failType);
            } else {
                if (DEBUG)  Log.d(TAG, "couldn't fire 'downloadFailed' , event obj.is null");
            }
        } else {
            if (Failure.INTERRUPT.equals(failType)) {
                if (DEBUG)  Log.e(TAG, "Thread interrupted");
                // TODO: release resources?!
            }
        }
    }
}
