package com.example.planehunter.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.planehunter.R;
import com.example.planehunter.data.firebase.FirebaseHandler;
import com.example.planehunter.model.LeaderboardEntry;
import com.example.planehunter.ui.adapters.LeaderboardAdapter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that displays the global leaderboard.
 * Shows the top players and the current user's personal rank if they are not in the top list.
 */
public class LeaderboardActivity extends AppCompatActivity {

    /** The maximum number of top players to display in the leaderboard list. */
    private static final int LEADERBOARD_LIMIT = 5;

    /** RecyclerView to display the list of top players. */
    private RecyclerView recyclerLeaderboard;
    /** Adapter for the leaderboard RecyclerView. */
    private LeaderboardAdapter adapter;

    /** Handler for interacting with Firebase services. */
    private FirebaseHandler firebaseHandler;
    /** Registration for the real-time leaderboard listener to stop updates on destroy. */
    private ListenerRegistration leaderboardListener;

    /** TextView displaying the current user's numerical rank. */
    private TextView textMyRank;
    /** TextView displaying the current user's name. */
    private TextView textMyName;
    /** TextView displaying the total number of aircraft captured by the current user. */
    private TextView textMyCaptures;
    /** TextView displaying the total experience points (XP) of the current user. */
    private TextView textMyXp;
    /** The layout container for the current user's personal rank information. */
    private View layoutMyRank;
    /** The title TextView for the personal rank section. */
    private TextView textMyRankTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        firebaseHandler = FirebaseHandler.getInstance();

        initLeaderboard();
        loadMyRank();
    }

    /**
     * Initializes the leaderboard RecyclerView and adapter.
     */
    private void initLeaderboard() {
        recyclerLeaderboard = findViewById(R.id.recyclerLeaderboard);
        textMyRank = findViewById(R.id.textMyRank);
        textMyName = findViewById(R.id.textMyName);
        textMyCaptures = findViewById(R.id.textMyCaptures);
        textMyXp = findViewById(R.id.textMyXp);
        layoutMyRank = findViewById(R.id.layoutMyRank);
        textMyRankTitle = findViewById(R.id.textMyRankTitle);


        adapter = new LeaderboardAdapter();
        recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        recyclerLeaderboard.setAdapter(adapter);

        listenToLeaderboard();

    }

    /**
     * Loads the current user's rank and stats from Firebase.
     */
    private void loadMyRank() {
        firebaseHandler.getMyLeaderboardRank(new FirebaseHandler.MyRankListener() {
            @Override
            public void onSuccess(@NonNull LeaderboardEntry entry, long rank) {
                String name = entry.name == null || entry.name.trim().isEmpty() ? "Player" : entry.name;

                textMyRank.setText(String.valueOf(rank));
                textMyName.setText(name);
                textMyCaptures.setText("Captures: " + entry.captures);
                textMyXp.setText(entry.xp + " XP");
            }

            @Override
            public void onError(@NonNull Exception e) {
                Log.d("LeaderboardActivity", "Failed to load my rank", e);

                textMyRank.setText("-");
                textMyName.setText("You");
                textMyCaptures.setText("Captures: 0");
                textMyXp.setText("0 XP");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (leaderboardListener != null) {
            leaderboardListener.remove();
            leaderboardListener = null;
        }
    }

    /**
     * Sets up a real-time listener for the top leaderboard entries.
     */
    private void listenToLeaderboard() {
        leaderboardListener = firebaseHandler
                .getLeaderboardTop(LEADERBOARD_LIMIT)
                .addSnapshotListener((value, error) -> {

                    if (error != null) {
                        Log.d("LeaderboardActivity", "Failed to listen to leaderboard", error);
                        return;
                    }

                    if (value == null) {
                        return;
                    }

                    List<LeaderboardEntry> items = new ArrayList<>();
                    String myUid = firebaseHandler.getUidOrNull();
                    boolean amIInTopList = false;

                    for (DocumentSnapshot doc : value.getDocuments()) {
                        LeaderboardEntry entry = firebaseHandler.toLeaderboardEntry(doc);
                        if (entry != null) {
                            items.add(entry);

                            // check if current user is inside top list
                            if (myUid != null && myUid.equals(entry.uid)) {
                                amIInTopList = true;
                            }
                        }
                    }

                    adapter.setItems(items);

                    int visibility = amIInTopList ? View.GONE : View.VISIBLE;
                    layoutMyRank.setVisibility(visibility);
                    textMyRankTitle.setVisibility(visibility);
                });
    }
}
