package com.example.planehunter.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.model.UserProfile;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class FirebaseHandler {

    private static volatile FirebaseHandler instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private FirebaseHandler() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static FirebaseHandler getInstance() {
        if (instance == null) {
            synchronized (FirebaseHandler.class) {
                if (instance == null) instance = new FirebaseHandler();
            }
        }
        return instance;
    }

    // -------------------------
    // Auth
    // -------------------------
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    @Nullable
    public String getUidOrNull() {
        FirebaseUser u = auth.getCurrentUser();
        return (u == null) ? null : u.getUid();
    }

    @NonNull
    public String getUidOrThrow() {
        String uid = getUidOrNull();
        if (uid == null) throw new IllegalStateException("Not signed-in");
        return uid;
    }

    public Task<AuthResult> signUpEmail(@NonNull String email, @NonNull String password) {
        return auth.createUserWithEmailAndPassword(email, password);
    }

    public Task<AuthResult> signInEmail(@NonNull String email, @NonNull String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    public void signOut() {
        auth.signOut();
    }

    // -------------------------
    // User Profile (/users/{uid})
    // -------------------------
    private DocumentReference myUserDoc() {
        return db.collection("users").document(getUidOrThrow());
    }

    public Task<Void> ensureDefaultProfile(@NonNull String displayName) {
        return myUserDoc().get().continueWithTask(t -> {
            DocumentSnapshot doc = t.getResult();
            if (doc != null && doc.exists()) {
                return Tasks.forResult(null);
            }
            UserProfile p = new UserProfile(getUidOrThrow(), displayName);
            return myUserDoc().set(p);
        });
    }

    public Task<DocumentSnapshot> getMyProfileSnap() {
        return myUserDoc().get();
    }

    public Task<UserProfile> getMyProfile() {
        return myUserDoc().get().continueWith(t -> {
            DocumentSnapshot doc = t.getResult();
            if (doc == null || !doc.exists()) return null;
            return doc.toObject(UserProfile.class);
        });
    }

    public Task<Void> saveMyProfile(@NonNull UserProfile profile) {
        // Safety: enforce uid match
        String uid = getUidOrThrow();
        profile.uid = uid;
        return myUserDoc().set(profile);
    }

    public Task<Void> updateMySettings(boolean notifyEnabled, int radiusKm) {
        return myUserDoc().update(
                "notifyEnabled", notifyEnabled,
                "radiusKm", radiusKm
        );
    }

    // -------------------------
    // Leaderboard (public read)
    // /leaderboard/{uid}
    // Fields expected: displayName, score, updatedAtMs (optional)
    // -------------------------
    public Query getLeaderboardTop(int limit) {
        return db.collection("leaderboard")
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(limit);
    }

    public Task<DocumentSnapshot> getLeaderboardEntry(@NonNull String uid) {
        return db.collection("leaderboard").document(uid).get();
    }
}