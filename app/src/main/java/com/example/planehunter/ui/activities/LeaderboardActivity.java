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

public class LeaderboardActivity extends AppCompatActivity {

    private static final int LEADERBOARD_LIMIT = 50;

    private RecyclerView recyclerLeaderboard;
    private LeaderboardAdapter adapter;

    private FirebaseHandler firebaseHandler;
    private ListenerRegistration leaderboardListener;

    private TextView textMyRank;
    private TextView textMyName;
    private TextView textMyCaptures;
    private TextView textMyXp;
    private View layoutMyRank;
    private TextView textMyRankTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        firebaseHandler = FirebaseHandler.getInstance();

        initLeaderboard();
        loadMyRank();
    }

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