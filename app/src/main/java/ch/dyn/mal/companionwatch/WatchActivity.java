package ch.dyn.mal.companionwatch;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class WatchActivity extends YouTubeBaseActivity {

    // Objects displayed on screen
    EditText searchInput;
    Button searchButton;
    ToggleButton visibilityToggle;
    FloatingActionButton floatingActionButton;

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
        floatingActionButton = findViewById(R.id.floatingActionButton);

        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CompanionWatch");
                String shareMessage= getString(R.string.invite) + "https://mal.dyn.ch/watch/" + namespace;
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.invite2)));
            }
        });

        // Initializing the Web-Socket
        try {
            IO.Options opt = new IO.Options();
            opt.query = "ns=".concat(namespace);
            opt.forceNew = true;
            mSocket = IO.socket("https://mal.dyn.ch/watch", opt);
        } catch (URISyntaxException ignored) {}

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
                                double seconds = Double.parseDouble(args[0].toString());
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
                                JSONArray videos = (JSONArray) args[0];
                                try {
                                    // The parent for all videos
                                    LinearLayout mainLayout = findViewById(R.id.videoResultsConatiner);
                                    mainLayout.removeAllViews();
                                    // Create all items for the rooms
                                    for (int i = 0; i < videos.length(); i++) {
                                        final JSONObject room = videos.getJSONObject(i);
                                        final JSONObject snippet = room.getJSONObject("snippet");
                                        // The inflater for the rooms to be displayed
                                        View view = getLayoutInflater().inflate(R.layout.video_list_item, mainLayout, false);
                                        // Set the title
                                        TextView title = view.findViewById(R.id.listItemTitle);
                                        title.setText(snippet.getString("title"));
                                        // Set the thumbnail
                                        ImageView thumbnail = view.findViewById(R.id.listItemThumbnail);
                                        Picasso.get().load(snippet.getJSONObject("thumbnails").getJSONObject("high").getString("url")).into(thumbnail);
                                        // Set onClickListener for redirecting user into room
                                        view.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                String id = null;
                                                try {
                                                    id = room.getJSONObject("id").getString("videoId");
                                                } catch (JSONException e) {
                                                    e.printStackTrace();
                                                }
                                                mSocket.emit("videoChange", id);
                                                youTubePlayer.loadVideo(id);
                                            }
                                        });
                                        mainLayout.addView(view);
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
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
                mSocket.emit("videoSearch", query);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput (InputMethodManager.SHOW_FORCED, 0);
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Set youTubePlayer to fullscreen if orientation is landscape
        int newOrientation = newConfig.orientation;
        if (newOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            youTubePlayer.setFullscreen(true);
        } else if (newOrientation == Configuration.ORIENTATION_PORTRAIT) {
            youTubePlayer.setFullscreen(false);
        }
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
