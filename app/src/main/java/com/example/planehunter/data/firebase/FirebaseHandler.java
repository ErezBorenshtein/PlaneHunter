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
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

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
    // Firestore refs
    // -------------------------
    private DocumentReference myUserDoc() {
        return db.collection("users").document(getUidOrThrow());
    }

    private DocumentReference myLeaderboardDoc() {
        return db.collection("leaderboard").document(getUidOrThrow());
    }

    // -------------------------
    // Profile
    // -------------------------
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

    public Task<UserProfile> getMyProfile() {
        return myUserDoc().get().continueWith(t -> {
            DocumentSnapshot doc = t.getResult();
            if (doc == null || !doc.exists()) return null;
            return doc.toObject(UserProfile.class);
        });
    }

    public Task<Void> updateMySettings(boolean notifyEnabled, int radiusKm) {
        long now = System.currentTimeMillis();
        return myUserDoc().update(
                "notifyEnabled", notifyEnabled,
                "radiusKm", radiusKm,
                "updatedAtMs", now
        );
    }

    public interface ProfileListener {
        void onProfile(@Nullable UserProfile profile);
        void onError(@NonNull Exception e);
    }

    public ListenerRegistration listenToMyProfile(@NonNull ProfileListener listener) {
        return myUserDoc().addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    listener.onError(error);
                    return;
                }
                if (value == null || !value.exists()) {
                    listener.onProfile(null);
                    return;
                }
                listener.onProfile(value.toObject(UserProfile.class));
            }
        });
    }

    // -------------------------
    // XP / Catch (atomic)
    // -------------------------
    public Task<Void> addXpForCatch(long xpToAdd) {
        String uid = getUidOrThrow();
        long now = System.currentTimeMillis();

        return db.runTransaction(tx -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot snap = tx.get(userRef);

            if (!snap.exists()) {
                // אם אין פרופיל עדיין, ניצור אחד מינימלי
                String fallbackName = "Player";
                UserProfile p = new UserProfile(uid, fallbackName);
                p.xp = xpToAdd;
                p.caughtCount = 1;
                p.updatedAtMs = now;
                tx.set(userRef, p);
            } else {
                Long currentXp = snap.getLong("xp");
                Long currentCaught = snap.getLong("caughtCount");
                long newXp = (currentXp == null ? 0 : currentXp) + xpToAdd;
                long newCaught = (currentCaught == null ? 0 : currentCaught) + 1;

                Map<String, Object> upd = new HashMap<>();
                upd.put("xp", newXp);
                upd.put("caughtCount", newCaught);
                upd.put("updatedAtMs", now);
                tx.update(userRef, upd);
            }

            // Update leaderboard too (public read)
            DocumentReference lbRef = db.collection("leaderboard").document(uid);
            String displayName = snap.exists() ? snap.getString("displayName") : "Player";

            // נחשב XP חדש בזהירות: אם snap לא קיים, כבר שמנו xpToAdd
            long xpForLb;
            if (!snap.exists()) xpForLb = xpToAdd;
            else {
                Long currentXp = snap.getLong("xp");
                xpForLb = (currentXp == null ? 0 : currentXp) + xpToAdd;
            }

            Map<String, Object> lb = new HashMap<>();
            lb.put("displayName", displayName == null ? "Player" : displayName);
            lb.put("xp", xpForLb);
            lb.put("updatedAtMs", now);
            tx.set(lbRef, lb);

            return null;
        });
    }

    // -------------------------
    // Leaderboard reads
    // -------------------------
    public Query getLeaderboardTop(int limit) {
        return db.collection("leaderboard")
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(limit);
    }
}