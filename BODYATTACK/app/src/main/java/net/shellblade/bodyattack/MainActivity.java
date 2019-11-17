package net.shellblade.bodyattack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.util.Log;
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
    private static final float WAKE_WORD_VOLUME_DROP = 0.15f;

    private BAAssistant assistant;
    private MusicPlayer musicPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

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
            assistant.Start(this.getApplicationContext());
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
        assistant.setOnSessionEnded(() -> musicPlayer.increaseVolumeRelative(WAKE_WORD_VOLUME_DROP));

        Log.i(TAG, "Music player started successfully");
        return musicPlayer;
    }
}
