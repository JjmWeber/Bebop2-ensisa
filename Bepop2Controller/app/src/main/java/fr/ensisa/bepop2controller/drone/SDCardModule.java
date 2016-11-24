package fr.ensisa.bepop2controller.drone;

import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.parrot.arsdk.ardatatransfer.ARDATATRANSFER_ERROR_ENUM;
import com.parrot.arsdk.ardatatransfer.ARDataTransferException;
import com.parrot.arsdk.ardatatransfer.ARDataTransferManager;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMedia;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloader;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderCompletionListener;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderProgressListener;
import com.parrot.arsdk.arutils.ARUtilsManager;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

public class SDCardModule {

    private static final String TAG = "SDCardModule";
    private static final String DRONE_MEDIA_FOLDER = "internal_000";
    private static final String MOBILE_MEDIA_FOLDER = "/ARSDKMedias/";

    public interface Listener {
        void onMatchingMediasFound(int nbMedias);

        void onDownloadProgressed(String mediaName, int progress);

        void onDownloadComplete(String mediaName);
    }

    private final List<Listener> listeners;

    private ARDataTransferManager dataTransferManager;
    private ARUtilsManager ftpList;
    private ARUtilsManager ftpQueue;

    private boolean threadIsRunning;
    private boolean isCancelled;

    private int nbMediasToDownload;
    private int currentDownloadIndex;

    public SDCardModule(@NonNull ARUtilsManager ftpListManager, @NonNull ARUtilsManager ftpQueueManager) {
        threadIsRunning = false;
        listeners = new ArrayList<>();

        ftpList = ftpListManager;
        ftpQueue = ftpQueueManager;

        ARDATATRANSFER_ERROR_ENUM result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK;
        try {
            dataTransferManager = new ARDataTransferManager();
        } catch (ARDataTransferException e) {
            Log.e(TAG, "Exception", e);
            result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_ERROR;
        }

        if(result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
            String externalDirectory = Environment.getExternalStorageDirectory().toString().concat(MOBILE_MEDIA_FOLDER);
            File f = new File(externalDirectory);
            
            if(!(f.exists() && f.isDirectory())) {
                boolean success = f.mkdir();
                if(!success)
                    Log.e(TAG, "Failed to create the folder " + externalDirectory);
            }
            
            try {
                dataTransferManager.getARDataTransferMediasDownloader().createMediasDownloader(ftpList, ftpQueue, DRONE_MEDIA_FOLDER, externalDirectory);
            } catch (ARDataTransferException e) {
                Log.e(TAG, "Exception", e);
                result = e.getError();
            }
        }

        if(result != ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
            dataTransferManager.dispose();
            dataTransferManager = null;
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void getFlightMedias(final String runId) {
        if(!threadIsRunning) {
            threadIsRunning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<ARDataTransferMedia> mediaList = getMediaList();
                    ArrayList<ARDataTransferMedia> mediasFromRun = null;
                    nbMediasToDownload = 0;
                    
                    if((mediaList != null) && !isCancelled) {
                        mediasFromRun = getRunIdMatchingMedias(mediaList, runId);
                        nbMediasToDownload = mediasFromRun.size();
                    }

                    notifyMatchingMediasFound(nbMediasToDownload);

                    if((mediasFromRun != null) && (nbMediasToDownload != 0) && !isCancelled)
                        downloadMedias(mediasFromRun);

                    threadIsRunning = false;
                    isCancelled = false;
                }
            }).start();
        }
    }

    public void getTodaysFlightMedias() {
        if(!threadIsRunning) {
            threadIsRunning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<ARDataTransferMedia> mediaList = getMediaList();
                    ArrayList<ARDataTransferMedia> mediasFromDate = null;
                    nbMediasToDownload = 0;

                    if((mediaList != null) && !isCancelled) {
                        GregorianCalendar today = new GregorianCalendar();
                        mediasFromDate = getDateMatchingMedias(mediaList, today);
                        nbMediasToDownload = mediasFromDate.size();
                    }

                    notifyMatchingMediasFound(nbMediasToDownload);

                    if((mediasFromDate != null) && (nbMediasToDownload != 0) && !isCancelled)
                        downloadMedias(mediasFromDate);

                    threadIsRunning = false;
                    isCancelled = false;
                }
            }).start();
        }
    }

    public void cancelGetFlightMedias() {
        if(threadIsRunning) {
            isCancelled = true;
            ARDataTransferMediasDownloader mediasDownloader = null;

            if(dataTransferManager != null)
                mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();

            if(mediasDownloader != null)
                mediasDownloader.cancelQueueThread();
        }
    }

    private ArrayList<ARDataTransferMedia> getMediaList() {
        ArrayList<ARDataTransferMedia> mediaList = null;
        ARDataTransferMediasDownloader mediasDownloader = null;

        if(dataTransferManager != null)
            mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();

        if(mediasDownloader != null)
            try {
                int mediaListCount = mediasDownloader.getAvailableMediasSync(false);
                mediaList = new ArrayList<>(mediaListCount);
                for (int i = 0; ((i < mediaListCount) && !isCancelled) ; i++) {
                    ARDataTransferMedia currentMedia = mediasDownloader.getAvailableMediaAtIndex(i);
                    mediaList.add(currentMedia);
                }
            }
            catch (ARDataTransferException e) {
                Log.e(TAG, "Exception", e);
                mediaList = null;
            }
        return mediaList;
    }

    private @NonNull ArrayList<ARDataTransferMedia> getRunIdMatchingMedias(ArrayList<ARDataTransferMedia> mediaList,
                                                                           String runId) {
        ArrayList<ARDataTransferMedia> matchingMedias = new ArrayList<>();

        for (ARDataTransferMedia media : mediaList) {
            if(media.getName().contains(runId))
                matchingMedias.add(media);

            if(isCancelled)
                break;
        }

        return matchingMedias;
    }

    private ArrayList<ARDataTransferMedia> getDateMatchingMedias(ArrayList<ARDataTransferMedia> mediaList,
                                                                 GregorianCalendar matchingCal) {
        ArrayList<ARDataTransferMedia> matchingMedias = new ArrayList<>();
        Calendar mediaCal = new GregorianCalendar();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss", Locale.getDefault());

        for (ARDataTransferMedia media : mediaList) {
            String dateStr = media.getDate();
            try {
                Date mediaDate = dateFormatter.parse(dateStr);
                mediaCal.setTime(mediaDate);
                if((mediaCal.get(Calendar.DAY_OF_MONTH) == (matchingCal.get(Calendar.DAY_OF_MONTH))) &&
                        (mediaCal.get(Calendar.MONTH) == (matchingCal.get(Calendar.MONTH))) &&
                        (mediaCal.get(Calendar.YEAR) == (matchingCal.get(Calendar.YEAR)))) {
                    matchingMedias.add(media);
                }
            } catch (ParseException e) {
                Log.e(TAG, "Exception", e);
            }

            if(isCancelled)
                break;
        }
        return matchingMedias;
    }

    private void downloadMedias(@NonNull ArrayList<ARDataTransferMedia> matchingMedias) {
        currentDownloadIndex = 1;
        ARDataTransferMediasDownloader mediasDownloader = null;

        if(dataTransferManager != null)
            mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();

        if(mediasDownloader != null) {
            for (ARDataTransferMedia media : matchingMedias) {
                try {
                    mediasDownloader.addMediaToQueue(media, dlProgressListener, null, dlCompletionListener, null);
                } catch (ARDataTransferException e) {
                    Log.e(TAG, "Exception", e);
                }

                if(isCancelled)
                    break;
            }

            if(!isCancelled)
                mediasDownloader.getDownloaderQueueRunnable().run();
        }
    }

    private void notifyMatchingMediasFound(int nbMedias) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onMatchingMediasFound(nbMedias);
    }

    private void notifyDownloadProgressed(String mediaName, int progress) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onDownloadProgressed(mediaName, progress);
    }

    private void notifyDownloadComplete(String mediaName) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onDownloadComplete(mediaName);
    }

    private final ARDataTransferMediasDownloaderProgressListener dlProgressListener = new ARDataTransferMediasDownloaderProgressListener() {
        private int lastProgressSent = -1;

        @Override
        public void didMediaProgress(Object arg, ARDataTransferMedia media, float percent) {
            final int progressInt = (int) Math.floor(percent);
            if(lastProgressSent != progressInt) {
                lastProgressSent = progressInt;
                notifyDownloadProgressed(media.getName(), progressInt);
            }
        }
    };

    private final ARDataTransferMediasDownloaderCompletionListener dlCompletionListener = new ARDataTransferMediasDownloaderCompletionListener() {
        @Override
        public void didMediaComplete(Object arg, ARDataTransferMedia media, ARDATATRANSFER_ERROR_ENUM error) {
            notifyDownloadComplete(media.getName());
            currentDownloadIndex++;

            if(currentDownloadIndex > nbMediasToDownload) {
                ARDataTransferMediasDownloader mediasDownloader = null;

                if(dataTransferManager != null)
                    mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();

                if(mediasDownloader != null)
                    mediasDownloader.cancelQueueThread();
            }
        }
    };

}
