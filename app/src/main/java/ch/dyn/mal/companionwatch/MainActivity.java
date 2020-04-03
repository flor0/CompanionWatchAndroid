package ch.dyn.mal.companionwatch;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
        setContentView(R.layout.activity_main);

        Button createRoomButton = findViewById(R.id.create_room);
        createRoomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Send a post-request to /newroom
                RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                String url = "https://mal.dyn.ch/newroom";

                StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
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
}
