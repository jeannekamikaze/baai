package net.shellblade.bodyattack;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.snips.hermes.IntentMessage;
import ai.snips.hermes.SessionEndedMessage;
import ai.snips.nlu.ontology.Slot;
import ai.snips.platform.SnipsPlatformClient;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public class BAAssistant {

    private static final String TAG = "BAAssistant";

    private final SnipsPlatformClient client;

    // To extract values from Snips slots.
    private static final Pattern MATCH_VALUE = Pattern.compile("value=([a-zA-Z0-9\\.]+)");

    // Slot names.
    private static final String RELEASE = "release";
    private static final String TRACK = "track";

    // Callback functions.
    //
    // Hook these up to react to voice commands.
    private Function<Integer, Void> playRelease = (Integer release) -> { return null; };
    private Function<Integer, Void> playTrack = (Integer track) -> { return null; };
    private BiFunction<Integer, Integer, Void> playReleaseTrack = (Integer release, Integer track) -> { return null; };
    private Runnable playNextTrack = () -> {};
    private Runnable playPreviousTrack = () -> {};
    private Runnable playMusic = () -> {};
    private Runnable stopMusic = () -> {};
    private Runnable startOver = () -> {};
    private Runnable onWakeWordDetected = () -> {};
    private Runnable onSessionEnded = () -> {};

    public BAAssistant(SnipsPlatformClient client) {
        this.client = client;

        configureClient(client);
    }

    public void Start(Context context) {
        client.connect(context);
    }

    private void configureClient(final SnipsPlatformClient client) {
        final BAAssistant assistant = this;

        client.setOnPlatformReady(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                Log.d(TAG, "Snips is ready. Say the wake word!");
                return null;
            }
        });

        client.setOnPlatformError(
            new Function1<SnipsPlatformClient.SnipsPlatformError, Unit>() {
                @Override
                public Unit invoke(final SnipsPlatformClient.SnipsPlatformError
                                       snipsPlatformError) {
                    // Handle error
                    Log.d(TAG, "Error: " + snipsPlatformError.getMessage());
                    return null;
                }
            });

        client.setOnHotwordDetectedListener(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                // Wake word detected, start a dialog session
                Log.d(TAG, "Wake word detected!");
                onWakeWordDetected.run();
                client.startSession(null, new ArrayList<String>(),
                    false, null);
                return null;
            }
        });

        client.setOnSessionEndedListener(new Function1<SessionEndedMessage, Unit>() {
            @Override
            public Unit invoke(SessionEndedMessage sessionEndedMessage) {
                Log.d(TAG, "Session ended");
                onSessionEnded.run();
                return null;
            }
        });

        client.setOnIntentDetectedListener(new Function1<IntentMessage, Unit>() {
            @Override
            public Unit invoke(final IntentMessage intentMessage) {
                // Intent detected, so the dialog session ends here
                client.endSession(intentMessage.getSessionId(), null);

                try // Parsing of user input may fail.
                {
                    Log.d(TAG, "Intent detected: " + intentMessage.getIntent().getIntentName());

                    switch (intentMessage.getIntent().getIntentName()) {
                        case "msunet:playRelease": {
                            String release = getSlotByName(intentMessage, RELEASE);
                            if (!release.isEmpty()) {
                                assistant.playRelease.apply((int)Double.parseDouble(release));
                            }
                            break;
                        }
                        case "msunet:playTrack": {
                            String track = getSlotByName(intentMessage, TRACK);
                            if (!track.isEmpty()) {
                                assistant.playTrack.apply((int)Double.parseDouble(track));
                            }
                            break;
                        }
                        case "msunet:playReleaseTrack": {
                            String release = getSlotByName(intentMessage, RELEASE);
                            String track = getSlotByName(intentMessage, TRACK);
                            // TODO: this command is overlapping with playRelease.
                            if (!release.isEmpty()) {
                                if (!track.isEmpty()) {
                                    assistant.playReleaseTrack.apply((int)Double.parseDouble(release), (int)Double.parseDouble(track));
                                }
                                else {
                                    assistant.playRelease.apply((int)Double.parseDouble(release));
                                }
                            }
                            /*if (!release.isEmpty() && !track.isEmpty()) {
                                assistant.playReleaseTrack.apply((int)Double.parseDouble(release), (int)Double.parseDouble(track));
                            }*/
                            break;
                        }
                        case "msunet:playNextTrack": {
                            assistant.playNextTrack.run();
                            break;
                        }
                        case "msunet:playPreviousTrack": {
                            assistant.playPreviousTrack.run();
                            break;
                        }
                        case "msunet:playMusic": {
                            assistant.playMusic.run();
                            break;
                        }
                        case "msunet:stopMusic": {
                            assistant.stopMusic.run();
                            break;
                        }
                        case "msunet:startOver": {
                            assistant.startOver.run();
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                }
                catch (Exception e)
                {
                    Log.e(TAG, e.getMessage());
                    Log.e(TAG, "Stack trace", e);
                }

                return null;
            }
        });

        client.setOnSnipsWatchListener(new Function1<String, Unit>() {
            public Unit invoke(final String s) {
                Log.d(TAG, "Log: " + s);
                return null;
            }
        });
    }

    private static String getSlotByName(IntentMessage msg, String slotName) {
        for (Slot slot : msg.getSlots()) {
            if (slot.getSlotName().equals(slotName)) {
                // Snips returns a slot value string of the form:
                //
                //   "NumberValue(value=4.0)".
                //
                // We use a regex expression to match the "value=4.0" part and extract
                // the value "4.0".
                Matcher m = MATCH_VALUE.matcher(slot.getValue().toString());
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return "";
    }

    public void setPlayReleaseCallback(Function<Integer, Void> func) {
        this.playRelease = func;
    }
    public void setPlayTrackCallback(Function<Integer, Void> func) {
        this.playTrack = func;
    }
    public void setPlayReleaseTrackCallback(BiFunction<Integer, Integer, Void> func) {
        this.playReleaseTrack = func;
    }
    public void setPlayNextTrack(Runnable func) {
        this.playNextTrack = func;
    }
    public void setPlayPreviousTrack(Runnable func) {
        this.playPreviousTrack = func;
    }
    public void setPlayMusicCallback(Runnable func) {
        this.playMusic = func;
    }
    public void setStopMusicCallback(Runnable func) {
        this.stopMusic = func;
    }
    public void setStartOverCallback(Runnable func) {
        this.startOver = func;
    }
    public void setOnWakeWordDetected(Runnable func) {
        this.onWakeWordDetected = func;
    }
    public void setOnSessionEnded(Runnable func) {
        this.onSessionEnded = func;
    }
}
