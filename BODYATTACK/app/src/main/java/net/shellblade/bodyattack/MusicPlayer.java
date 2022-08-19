package net.shellblade.bodyattack;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.NotImplementedError;

public class MusicPlayer {

    private static final String TAG = "MusicPlayer";

    // Extracts the release and track numbers from strings such as the following:
    //
    //  /storage/emulated/0/Music/BODYATTACK 107/08 This Is Love (Extended Mix).mp3
    //  /storage/emulated/0/Music/BODYATTACK 107/EXP 052 Do It Again.mp3
    private static final Pattern MATCH_BODYATTACK_RELEASE = Pattern.compile("BODYATTACK ([0-9]+)\\/[a-zA-Z]*\\s*([0-9]+)");

    private final Context context;
    private MediaPlayer mediaPlayer;
    private float volume = 1.0f;

    private int currentRelease = -1;
    private int currentTrack = 1;

    // Maps release to track to song file path.
    private final HashMap<Integer, HashMap<Integer, String>> songDatabase = new HashMap<>();

    public MusicPlayer(Context context) {
        this.context = context;

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playNextTrack();
            }
        });

        buildSongDatabase();
        currentRelease = getLatestRelease();
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void playRelease(int release) {
        Log.i(TAG, "Playing BODYATTACK release " + release);
        tryPlayReleaseAndTrack(release, 1);
    }

    public void playTrack(int track) {
        Log.i(TAG, "Playing track " + track);
        tryPlayReleaseAndTrack(currentRelease, track);
    }

    public void playReleaseTrack(int release, int track) {
        Log.i(TAG, "Playing BODYATTACK release " + release + ", track " + track);
        tryPlayReleaseAndTrack(release, track);
    }

    public void playNextTrack() {
        Log.i(TAG, "Playing next track");
        tryPlayReleaseAndTrack(currentRelease, currentTrack+1);
    }

    public void playPreviousTrack() {
        Log.i(TAG, "Playing previous track");
        tryPlayReleaseAndTrack(currentRelease, currentTrack-1);
    }

    public void playMusic() {
        Log.i(TAG, "Playing music");
        try {
            mediaPlayer.start();
        }
        catch (Exception e) {}
    }

    public void stopMusic() {
        Log.i(TAG, "Stopping music");
        try {
            mediaPlayer.pause(); // stop() stops in a way that cannot resume, so using pause() here.
        }
        catch (Exception e) {}
    }

    public void startOver() {
        Log.i(TAG, "Starting over");
        tryPlayReleaseAndTrack(currentRelease, currentTrack);
    }

    public void setVolume(float percent) {
        volume = Math.max(0.0f, Math.min(1.0f, percent));
        mediaPlayer.setVolume(volume, volume);
        Log.d(TAG, "Volume: " + volume + "%");
    }

    /// Increase the volume.
    public void increaseVolume(float percent) {
        setVolume(volume + percent);
    }

    /// Decrease the volume.
    public void decreaseVolume(float percent) {
        setVolume(volume - percent);
    }

    /// Increase the volume relative to the current volume.
    public void increaseVolumeRelative(float percent) {
        setVolume(volume + volume*percent);
    }

    /// Decrease the volume relative to the current volume.
    public void decreaseVolumeRelative(float percent) {
        setVolume(volume - volume*percent);
    }

    private void tryPlayReleaseAndTrack(int release, int track) {
        if (trySetReleaseAndTrack(release, track)) {
            try  {
                playSongFromStart();
            }
            catch (Exception e) {
                Log.e(TAG, e.getMessage());
                Log.e(TAG, "Stack trace", e);
            }
        }
    }

    private boolean trySetReleaseAndTrack(int release, int track) {
        HashMap<Integer, String> trackMap = songDatabase.get(release);
        if (trackMap == null) {
            Log.d(TAG, "No track map for release " + release);
            return false;
        }
        if (trackMap.get(track) == null) {
            Log.d(TAG, "No track " + track + " for release " + release);
            return false;
        }

        this.currentRelease = release;
        this.currentTrack = track;
        return true;
    }

    private String getCurrentTrackFilePath() {
        return songDatabase.get(currentRelease).get(currentTrack);
    }

    private void playSongFromStart() throws IOException {
        String songFilePath = getCurrentTrackFilePath();
        mediaPlayer.stop();
        mediaPlayer.reset();
        mediaPlayer.setDataSource(songFilePath);
        mediaPlayer.prepare();
        mediaPlayer.start();
        Log.d(TAG, "Playing track: " + songFilePath);
    }

    private int getLatestRelease() {
        return -1; // TODO: implement this
    }

    // TODO: DATA in MediaColumns has been deprecated.
    @SuppressWarnings("deprecation")
    private void buildSongDatabase() {
        Log.d(TAG, "Building song database");

        Cursor cursor = context.getContentResolver().query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            new String[] { MediaStore.Audio.Media.DATA },
            MediaStore.Audio.Media.IS_MUSIC + "!= 0",
            null,
            MediaStore.Audio.Media.DATA + " ASC");

        while(cursor != null && cursor.moveToNext()){;
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String songFilePath = cursor.getString(column_index);

            Matcher matcher = MATCH_BODYATTACK_RELEASE.matcher(songFilePath);
            if (matcher.find()) {
                try { // Parsing of the release and track number may fail.
                    int release = Integer.parseInt(matcher.group(1));
                    int track = Integer.parseInt(matcher.group(2));

                    Log.d(TAG, "Found song: " + songFilePath + ", release: " + release + ", track: " + track);

                    if (songDatabase.get(release) == null) {
                        songDatabase.put(release, new HashMap<>());
                    }
                    songDatabase
                        .get(release)
                        .put(track, songFilePath);
                }
                catch (Exception e) { }
            }
        }
        cursor.close();

        Log.d(TAG, "Song database:");
        for (HashMap.Entry<Integer, HashMap<Integer, String>> release : songDatabase.entrySet()) {
            for (HashMap.Entry<Integer, String> track : release.getValue().entrySet()) {
                Log.d(TAG, "Release: " + release.getKey() + ", track: " + track.getKey() + ", file path: " + track.getValue());
            }
        }
    }
}
