package ch.dyn.mal.companionwatch;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;

import org.json.JSONObject;

import java.net.URISyntaxException;

public class WatchActivity extends YouTubeBaseActivity {

    // Objects displayed on screen
    EditText searchInput;
    Button searchButton;
    ToggleButton visibilityToggle;

    // The Web-Socket
    Socket mSocket;

    // The namespace of the socket
    String namespace;

    // Event listeners for the Web-Socket
    Emitter.Listener onStateChange;
    Emitter.Listener onTimeChange;
    Emitter.Listener onVideoChange;
    Emitter.Listener onVisibilityChange;
    Emitter.Listener onSearchResults;

    // Variables needed for the YouTube video-player
    YouTubePlayerView youTubePlayerView;
    YouTubePlayer youTubePlayer;
    YouTubePlayer.OnInitializedListener onInitializedListener;

    // A boolean indicating whether the last state change has been caused by the user or by a socket event
    boolean externalChange;

    // A boolean indicating whether the user has newly joined the room and plays the video for the first time
    boolean firstPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);

        namespace = getIntent().getExtras().getString("ns");

        externalChange = false;
        firstPlay = true;

        // Objects displayed on screen
        searchInput = findViewById(R.id.editText);
        searchButton = findViewById(R.id.button);
        visibilityToggle = findViewById(R.id.visibilityToggle);

        // Initializing the Web-Socket
        try {
            IO.Options opt = new IO.Options();
            opt.query = "ns=".concat(namespace);
            opt.forceNew = true;
            mSocket = IO.socket("https://mal.dyn.ch/watch", opt);
        } catch (URISyntaxException e) {}

        mSocket.connect();

        // Initializing the youtube player
        youTubePlayerView = findViewById(R.id.youtube_player);
        onInitializedListener = new YouTubePlayer.OnInitializedListener() {

            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer newYouTubePlayer, boolean b) {

                youTubePlayer = newYouTubePlayer;

                // Add event listener to youTubePlayer
                youTubePlayer.setPlaybackEventListener(new YouTubePlayer.PlaybackEventListener() {
                    @Override
                    public void onPlaying() {
                        // Request a time sync if the user newly joined the room and the video starts playing
                        if (firstPlay) {
                            youTubePlayer.pause();
                            externalChange = true;
                            mSocket.emit("requestStateSync");
                            mSocket.emit("requestTimeSync");
                            firstPlay = false;
                            return;
                        } else {
                            // Don't emit an event if the state change was caused by another client
                            if (externalChange) externalChange = false;
                            else {
                                double millis = youTubePlayer.getCurrentTimeMillis() / 1000.0;
                                mSocket.emit("stateChange", 1, millis);
                            }
                        }
                    }

                    @Override
                    public void onPaused() {
                        // Don't emit an event if the state change was caused by another client
                        if (externalChange) externalChange = false;
                        else {
                            double millis = youTubePlayer.getCurrentTimeMillis() / 1000.0;
                            mSocket.emit("stateChange", 2, millis);
                        }
                    }

                    @Override
                    public void onSeekTo(int i) {
                        // Don't emit an event if the state change was caused by another client
                        if (externalChange) externalChange = false;
                        else {
                            double millis = i / 1000.0;
                            mSocket.emit("timeChange", millis);
                        }
                    }

                    @Override
                    public void onStopped() {

                    }

                    @Override
                    public void onBuffering(boolean b) {

                    }
                });

                // ----------------- Start of Emitter listeners declaration -----------------

                onStateChange = new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                int data = (int) args[0];
                                switch (data) {
                                    case 1: youTubePlayer.play(); externalChange = true; break;
                                    case 2: youTubePlayer.pause(); externalChange = true; break;
                                }
                            }
                        });
                    }
                };

                onTimeChange = new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                externalChange = true;
                                double seconds = Double.valueOf(args[0].toString());
                                int millis = (int) (seconds * 1000.0);
                                youTubePlayer.seekToMillis(millis);
                            }
                        });
                    }
                };

                onVideoChange = new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String videoId = (String) args[0];
                                youTubePlayer.loadVideo(videoId);

                            }
                        });
                    }
                };

                onVisibilityChange = new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                boolean visibility = (boolean) args[0];
                                visibilityToggle.setChecked(visibility);
                            }
                        });
                    }
                };

                onSearchResults = new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                            }
                        });
                    }
                };

                // ------------------ End of Emitter listeners declaration ------------------

                // Event listeners for the Web-Socket
                mSocket.on("stateChange", onStateChange);
                mSocket.on("timeChange", onTimeChange);
                mSocket.on("videoChange", onVideoChange);
                mSocket.on("visibilityChange", onVisibilityChange);
                mSocket.on("searchResults", onSearchResults);

                // Emit events to sync the current players state with other watchers
                mSocket.emit("requestVideoSync");
                mSocket.emit("requestVisibilitySync");
                mSocket.emit("requestStateSync");
            }

            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

            }
        };

        // Initialize the youtube video player
        youTubePlayerView.initialize(DeveloperVariables.API_KEY, onInitializedListener);

        // Perform a search if the button is pressed
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = searchInput.getText().toString();
            }
        });

        // Emit an event for changing the rooms visibility when the visibilityToggle Button is clicked
        visibilityToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSocket.emit("changeVisibility", visibilityToggle.isChecked());
            }
        });

        // Perform a search when the user submits a query via the keyboard (Bottom-right button on keyboard)
        searchInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchButton.performClick();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        youTubePlayer.release();
        youTubePlayer = null;

        mSocket.disconnect();
        mSocket.off("stateChange", onStateChange);
        mSocket.off("timeChange", onTimeChange);
        mSocket.off("videoChange", onVideoChange);
        mSocket.off("visibilityChange", onVisibilityChange);
        mSocket.off("searchResults", onSearchResults);
    }
}
