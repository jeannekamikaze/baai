package net.shellblade.bodyattack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.gesture.Gesture;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ai.snips.platform.SnipsPlatformClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String ASSISTANT_FILE = "assistant.zip";
    private static final String SNIPS_DIRECTORY = "snips";

    private static final String[] APP_PERMISSIONS = new String[] {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };

    // Percent of music volume to decrease when the wake word is detected.
    // The volume is then increased by the same amount when the session ends.
    private static final float WAKE_WORD_VOLUME_DROP = 0.55f;

    private BAAssistant assistant;
    private MusicPlayer musicPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        hideSystemUI();

        if (ensurePermissions()) {
            startApp();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onDestroy() {
        stopApp();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private boolean permissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission " + permission + " has not been granted");
                return false;
            }
            Log.d(TAG, "Permission " + permission + " granted");
        }
        return true;
    }

    private boolean ensurePermissions() {
        if (!permissionsGranted(APP_PERMISSIONS)) {
            Log.d(TAG, "Requesting permissions");
            ActivityCompat.requestPermissions(this, APP_PERMISSIONS, 0);
            return false;
        }
        Log.d(TAG, "Permissions granted");
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 0 && grantResults.length > 0 && eq(grantResults, PackageManager.PERMISSION_GRANTED)) {
            startApp();
        }
    }

    private static boolean eq(int[] xs, int val) {
        for (int x : xs) {
            if (x != val) {
                return false;
            }
        }
        return true;
    }

    private void startApp() {
        assistant = startAssistant();
        musicPlayer = startMusicPlayer(assistant);
    }

    private void stopApp() {
        // Release the music player and assistant when the application is destroyed.
        // Otherwise, two or more Snips sessions can be active when the application is recreated,
        // which them causes the application to play the same song multiple times simultaneously
        // (one per instance).
        if (musicPlayer != null) {
            musicPlayer.release();
            musicPlayer = null;
        }
        if (assistant != null) {
            assistant.release();
            assistant = null;
        }
    }

    private BAAssistant startAssistant() {
        Log.i(TAG, "Starting snips");
        try {
            // To test this from scratch, run the following command in an adb shell:
            //
            //   run-as net.shellblade.bodyattack rm -rf /data/user/0/net.shellblade.bodyattack/files/snips
            InputStream assistantZipFile = getBaseContext().getAssets().open(ASSISTANT_FILE);
            File assistantLocation = new File(getFilesDir(), SNIPS_DIRECTORY);
            SnipsPlatformClient client = Snips.createClient(assistantZipFile, assistantLocation);
            BAAssistant assistant = new BAAssistant(client);
            assistant.start(this.getApplicationContext());
            Log.i(TAG, "Snips started successfully!");
            return assistant;
        }
        catch (IOException e) {
            Log.e(TAG, e.getMessage());
            Log.e(TAG, "Stack trace", e);
        }
        return null;
    }

    private MusicPlayer startMusicPlayer(BAAssistant assistant) {
        Log.i(TAG, "Starting the music player");
        MusicPlayer musicPlayer = new MusicPlayer(getApplicationContext());

        assistant.setPlayReleaseCallback((Integer release) -> {
            musicPlayer.playRelease(release);
            return null; // TODO: what kind of Function would make this unnecessary?
        });
        assistant.setPlayTrackCallback((Integer track) -> {
            musicPlayer.playTrack(track);
            return null; // TODO: what kind of Function would make this unnecessary?
        });
        assistant.setPlayReleaseTrackCallback((Integer release, Integer track) -> {
            musicPlayer.playReleaseTrack(release, track);
            return null; // TODO: what kind of Function would make this unnecessary?
        });

        assistant.setPlayNextTrack(() -> musicPlayer.playNextTrack());
        assistant.setPlayPreviousTrack(() -> musicPlayer.playPreviousTrack());
        assistant.setPlayMusicCallback(() -> musicPlayer.playMusic());
        assistant.setStopMusicCallback(() -> musicPlayer.stopMusic());
        assistant.setStartOverCallback(() -> musicPlayer.startOver());

        assistant.setOnWakeWordDetected(() -> musicPlayer.decreaseVolumeRelative(WAKE_WORD_VOLUME_DROP));
        assistant.setOnSessionEnded(() -> musicPlayer.setVolume(1.0f));

        Log.i(TAG, "Music player started successfully");
        return musicPlayer;
    }
}
