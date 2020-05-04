package ch.dyn.mal.companionwatch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.icu.text.MeasureFormat;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.okhttp.internal.Platform;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

public class MainActivity extends AppCompatActivity {

    // Queue for http requests
    RequestQueue queue;

    // Base url to where the requests will be sent
    final String baseUrl = "https://mal.dyn.ch/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Send user to correct room if opened by link
        try {
            String path = Objects.requireNonNull(getIntent().getData()).getPath();
            System.out.println(path);
            // Connect user to correct room
            if (path != null && !path.equals("/")) {
                Intent i = new Intent(getApplicationContext(), WatchActivity.class);
                Bundle b = new Bundle();
                b.putString("ns", path.split("/")[2]);
                i.putExtras(b);
                startActivity(i);
            }
        }
        // User opened app by themselves
        catch (NullPointerException ignored) {}
        setContentView(R.layout.activity_main);

        final SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadRooms();
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        queue = Volley.newRequestQueue(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadRooms();

        Button createRoomButton = findViewById(R.id.create_room);
        createRoomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Send a post-request to /newroom
                StringRequest stringRequest = new StringRequest(Request.Method.POST, baseUrl + "newroom", new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Intent i = new Intent(getApplicationContext(), WatchActivity.class);
                        Bundle b = new Bundle();
                        b.putString("ns", response.split("/")[2]);
                        i.putExtras(b);
                        startActivity(i);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(getApplicationContext(), getString(R.string.newRoomError), Toast.LENGTH_LONG).show();
                    }
                });
                queue.add(stringRequest);
            }
        });
    }

    // Displays all public rooms
    private void loadRooms() {
        // Get request on homepage for getting public rooms
        StringRequest stringRequest = new StringRequest(Request.Method.GET, baseUrl, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                // Get all rooms from the response
                Pattern pattern = Pattern.compile("let rooms = (.*)[;\n]+");
                Matcher matcher = pattern.matcher(response);
                matcher.find();
                JSONArray rooms;
                try {
                    // The parent for all videos
                    LinearLayout mainLayout = findViewById(R.id.publicRoomsContainer);
                    mainLayout.removeAllViews();
                    // Create list elements
                    rooms = new JSONArray(matcher.group(1));
                    // Create text signaling that no rooms are available if that is the case
                    if (rooms.length() == 0) {
                        // Create the text view and set properties for it
                        TextView noRoomsMessage = new TextView(getApplicationContext());
                        noRoomsMessage.setText(getString(R.string.noRoomsMessage));
                        noRoomsMessage.setGravity(Gravity.CENTER);
                        noRoomsMessage.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getApplicationContext().getResources().getDisplayMetrics()));
                        mainLayout.addView(noRoomsMessage);
                    }
                    // Create all items for the rooms
                    for (int i = 0; i < rooms.length(); i++) {
                        final JSONObject room = rooms.getJSONObject(i);
                        JSONObject snippet = room.getJSONObject("snippet");
                        // The inflater for the rooms to be displayed
                        View view = getLayoutInflater().inflate(R.layout.room_list_item, mainLayout, false);
                        // Set the title
                        TextView title = view.findViewById(R.id.listItemTitle);
                        title.setText(snippet.getString("title"));
                        // Set the amount of companions
                        TextView companions = view.findViewById(R.id.listItemCompanions);
                        String companionText = getString(R.string.companions) + " " + room.getInt("connectedClients");
                        companions.setText(companionText);
                        // Set the thumbnail
                        ImageView thumbnail = view.findViewById(R.id.listItemThumbnail);
                        Picasso.get().load(snippet.getJSONObject("thumbnails").getJSONObject("standard").getString("url")).into(thumbnail);
                        // Set onClickListener for redirecting user into room
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    Intent i = new Intent(getApplicationContext(), WatchActivity.class);
                                    Bundle b = new Bundle();
                                    b.putString("ns", room.getString("roomId"));
                                    i.putExtras(b);
                                    startActivity(i);
                                } catch (JSONException e) {
                                    Toast.makeText(getApplicationContext(), getString(R.string.roomConnectionError), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        mainLayout.addView(view);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(getApplicationContext(), getString(R.string.publicRoomsError), Toast.LENGTH_LONG).show();
            }
        });

        queue.add(stringRequest);
    }
}
