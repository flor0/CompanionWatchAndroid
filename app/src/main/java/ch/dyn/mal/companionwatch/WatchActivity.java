package ch.dyn.mal.companionwatch;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;

public class WatchActivity extends YouTubeBaseActivity {

    YouTubePlayerView youTubePlayerView;
    YouTubePlayer.OnInitializedListener onInitializedListener;

    EditText searchInput;
    Button searchButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);

        searchInput = findViewById(R.id.editText);
        searchButton = findViewById(R.id.button);

        youTubePlayerView = findViewById(R.id.youtube_player);

        onInitializedListener = new YouTubePlayer.OnInitializedListener() {

            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {

                youTubePlayer.loadVideo("hMAPyGoqQVw");

                youTubePlayer.play();

            }

            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

            }
        };

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = searchInput.getText().toString();

                youTubePlayerView.initialize(DeveloperVariables.API_KEY, onInitializedListener);
            }
        });

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
}
