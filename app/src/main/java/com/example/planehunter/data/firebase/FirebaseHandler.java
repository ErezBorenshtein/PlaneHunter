package com.example.planehunter.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.model.Plane;
import com.example.planehunter.model.PlaneCapture;
import com.example.planehunter.model.UserProfile;
import com.example.planehunter.model.LeaderboardEntry;

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
import com.google.firebase.firestore.SetOptions;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FirebaseHandler {

    private static volatile FirebaseHandler instance;

    private static final long CAPTURE_COOLDOWN_MS = 30L * 60L * 1000L;
    //private static final long CAPTURE_COOLDOWN_MS = 0;

    private static final long XP_TIER_1 = 1000L;
    private static final long XP_TIER_2 = 1300L;
    private static final long XP_TIER_3 = 1600L;
    private static final long XP_TIER_4 = 2000L;
    private static final long XP_TIER_5 = 2600L;

    private static final double FIRST_TIME_MULTIPLIER = 2.0;
    private static final double REPEAT_MULTIPLIER = 0.3;



    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private FirebaseHandler() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static FirebaseHandler getInstance() {
        if (instance == null) {
            synchronized (FirebaseHandler.class) {
                if (instance == null) {
                    instance = new FirebaseHandler();
                }
            }
        }
        return instance;
    }

    public static class CaptureAwardResult {
        public boolean awarded;
        public boolean firstTime;
        public boolean cooldownActive;
        public long xpAwarded;
        public long cooldownRemainingMs;

        public CaptureAwardResult() {
        }
    }

    public interface ProfileListener {
        void onProfile(@Nullable UserProfile profile);
        void onError(@NonNull Exception e);
    }

    //to show the rank
    public interface MyRankListener {
        void onSuccess(@NonNull LeaderboardEntry entry, long rank);
        void onError(@NonNull Exception e);
    }

    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    @Nullable
    public String getUidOrNull() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @NonNull
    public String getUidOrThrow() {
        String uid = getUidOrNull();
        if (uid == null) {
            throw new IllegalStateException("Not signed-in");
        }
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

    private DocumentReference myUserDoc() {
        return db.collection("users").document(getUidOrThrow());
    }

    private DocumentReference myLeaderboardDoc() {
        return db.collection("leaderboard").document(getUidOrThrow());
    }

    private DocumentReference myCaptureDoc(@NonNull String captureKey) {
        return myUserDoc().collection("captures").document(captureKey);
    }

    public Task<Void> ensureDefaultProfile(@NonNull String displayName) {
        return myUserDoc().get().continueWithTask(task -> {
            DocumentSnapshot doc = task.getResult();

            if (doc != null && doc.exists()) {
                return Tasks.forResult(null);
            }

            UserProfile profile = new UserProfile(getUidOrThrow(), displayName);
            return myUserDoc().set(profile);
        });
    }

    public Task<UserProfile> getMyProfile() {
        return myUserDoc().get().continueWith(task -> {
            DocumentSnapshot doc = task.getResult();

            if (doc == null || !doc.exists()) {
                return null;
            }

            return doc.toObject(UserProfile.class);
        });
    }

    public Task<Void> updateAlertCategories(@NonNull ArrayList<Long> alertCategories) {
        UserProfile profile = new UserProfile();
        profile.alertCategories = new ArrayList<>(alertCategories);
        profile.updatedAtMs = System.currentTimeMillis();

        return myUserDoc().set(profile, SetOptions.merge());
    }

    public Task<Void> updateMySettings(boolean notifyEnabled, int radiusKm) {
        UserProfile profile = new UserProfile();
        profile.notifyEnabled = notifyEnabled;
        profile.radiusKm = radiusKm;
        profile.updatedAtMs = System.currentTimeMillis();

        return myUserDoc().set(profile, SetOptions.merge());
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

    public Task<CaptureAwardResult> awardCaptureXp(@NonNull Plane plane) {
        String uid = getUidOrThrow();
        long now = System.currentTimeMillis();

        String captureKey = buildCaptureKey(plane);
        long baseXp = resolveBaseXp(plane);

        return db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentReference captureRef = userRef.collection("captures").document(captureKey);
            DocumentReference leaderboardRef = db.collection("leaderboard").document(uid);

            DocumentSnapshot userSnap = transaction.get(userRef);
            DocumentSnapshot captureSnap = transaction.get(captureRef);

            UserProfile profile = buildProfileForTransaction(userSnap, uid);
            CaptureAwardResult result = new CaptureAwardResult();

            boolean firstTime = !captureSnap.exists();

            if (!firstTime) {
                long lastCaughtAtMs = getLong(captureSnap, "lastCaughtAtMs");
                long elapsedMs = now - lastCaughtAtMs;

                if (elapsedMs < CAPTURE_COOLDOWN_MS) {
                    result.awarded = false;
                    result.firstTime = false;
                    result.cooldownActive = true;
                    result.xpAwarded = 0L;
                    result.cooldownRemainingMs = CAPTURE_COOLDOWN_MS - elapsedMs;
                    return result;
                }
            }

            long xpAwarded = firstTime
                    ? Math.round(baseXp * FIRST_TIME_MULTIPLIER)
                    : Math.round(baseXp * REPEAT_MULTIPLIER);

            profile.xp += xpAwarded;
            profile.caughtCount += 1L;
            profile.updatedAtMs = now;

            if (firstTime) {
                profile.uniqueRegistrationsCount += 1L;
            }

            PlaneCapture capture = buildPlaneCaptureForWrite(plane, now, captureSnap, firstTime);

            transaction.set(userRef, profile, SetOptions.merge());
            transaction.set(captureRef, capture, SetOptions.merge());
            transaction.set(leaderboardRef, buildLeaderboardEntry(profile.uid ,profile.displayName, profile.xp,profile.caughtCount),
                    SetOptions.merge());

            result.awarded = true;
            result.firstTime = firstTime;
            result.cooldownActive = false;
            result.xpAwarded = xpAwarded;
            result.cooldownRemainingMs = 0L;

            return result;
        });
    }

    @Nullable
    public LeaderboardEntry toLeaderboardEntry(@NonNull DocumentSnapshot doc) {
        //convert DocumentSnapshot to LeaderboardEntry

        LeaderboardEntry entry = doc.toObject(LeaderboardEntry.class);

        if (entry == null) {
            return null;
        }

        if (entry.name == null || entry.name.trim().isEmpty()) {
            entry.name = "Player";
        }

        return entry;
    }


    public void getMyLeaderboardRank(@NotNull MyRankListener listener){
        String uid = getUidOrThrow();

        myLeaderboardDoc().get()
                .addOnSuccessListener(doc->{
                   if(!doc.exists()){
                       listener.onError(new IllegalStateException("User is not on leaderboard"));
                   }

                   LeaderboardEntry entry = doc.toObject(LeaderboardEntry.class);
                   if(entry ==null){
                       listener.onError(new IllegalStateException("Failed to parse leaderboard entry"));
                       return;
                   }

                   long xp = doc.getLong("xp") !=null ? doc.getLong("xp") :0;

                   db.collection("leaderboard").whereGreaterThan("xp",xp)
                           .get()
                           .addOnSuccessListener(aboveQuery->{
                               long rank = aboveQuery.size() +1L;
                               listener.onSuccess(entry,rank);
                           })
                           .addOnFailureListener(listener::onError);
                })
                .addOnFailureListener(listener::onError);

    }



    public Task<Set<String>> getMyPlanesInCooldown() {
        //for showing the planes in cooldown in gray
        long now = System.currentTimeMillis();

        return myUserDoc()
                .collection("captures")
                .get()
                .continueWith(task -> {
                    Set<String> cooldownIcaos = new HashSet<>();

                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        if (e != null) throw e;
                        return cooldownIcaos;
                    }

                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        //for each plane check if in cooldown
                        long lastCaughtAtMs = getLong(doc, "lastCaughtAtMs");
                        long elapsedMs = now - lastCaughtAtMs;

                        if (elapsedMs >= CAPTURE_COOLDOWN_MS) {
                            continue;
                        }

                        String icao24 = doc.getString("icao24");
                        if (icao24 == null || icao24.trim().isEmpty()) {
                            continue;
                        }

                        cooldownIcaos.add(icao24.trim().toUpperCase(Locale.US));
                    }

                    return cooldownIcaos;
                });
    }

    public Query getLeaderboardTop(int limit) {
        return db.collection("leaderboard")
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(limit);
    }

    private UserProfile buildProfileForTransaction(@NonNull DocumentSnapshot userSnap, @NonNull String uid) {
        UserProfile profile = userSnap.toObject(UserProfile.class);

        if (profile == null) {
            profile = new UserProfile(uid, "Player");
            return profile;
        }

        if (profile.uid == null || profile.uid.trim().isEmpty()) {
            profile.uid = uid;
        }

        if (profile.displayName == null || profile.displayName.trim().isEmpty()) {
            profile.displayName = "Player";
        }

        return profile;
    }

    private PlaneCapture buildPlaneCaptureForWrite(
            @NonNull Plane plane,
            long now,
            @NonNull DocumentSnapshot existingCaptureSnap,
            boolean firstTime
    ) {
        if (firstTime) {
            return new PlaneCapture(
                    emptyToNull(plane.getRegistration()),
                    emptyToNull(plane.getIcao24()),
                    emptyToNull(plane.getTypeCode()),
                    emptyToNull(plane.getTypeName()),
                    emptyToNull(plane.getCallSign()),
                    now
            );
        }

        PlaneCapture capture = existingCaptureSnap.toObject(PlaneCapture.class);
        if (capture == null) {
            return new PlaneCapture(
                    emptyToNull(plane.getRegistration()),
                    emptyToNull(plane.getIcao24()),
                    emptyToNull(plane.getTypeCode()),
                    emptyToNull(plane.getTypeName()),
                    emptyToNull(plane.getCallSign()),
                    now
            );
        }

        capture.registration = preferNonEmpty(capture.registration, plane.getRegistration());
        capture.icao24 = preferNonEmpty(capture.icao24, plane.getIcao24());
        capture.typeCode = preferNonEmpty(capture.typeCode, plane.getTypeCode());
        capture.typeName = preferNonEmpty(capture.typeName, plane.getTypeName());
        capture.callSign = preferNonEmpty(capture.callSign, plane.getCallSign());

        if (capture.firstCaughtAtMs <= 0L) {
            capture.firstCaughtAtMs = now;
        }

        capture.lastCaughtAtMs = now;
        capture.timesCaught = Math.max(1L, capture.timesCaught + 1L);

        return capture;
    }

    private LeaderboardEntry buildLeaderboardEntry(@NonNull String uid,@NonNull String name, long xp, long captures) {
        LeaderboardEntry entry = new LeaderboardEntry(uid,name,xp,captures);

        return entry;
    }

    private String buildCaptureKey(@NonNull Plane plane) {
        String icao24 = normalizeKeyPart(plane.getIcao24());

        if (!icao24.isEmpty()) {
            return "ICAO_" + icao24;
        }

        throw new IllegalStateException("Plane must have icao24");
    }

    @Nullable
    private String preferNonEmpty(@Nullable String existingValue, @Nullable String newValue) {
        String normalizedNewValue = emptyToNull(newValue);
        if (normalizedNewValue != null) {
            return normalizedNewValue;
        }

        return emptyToNull(existingValue);
    }

    private long resolveBaseXp(@NonNull Plane plane) {
        String typeCode = normalizeForMatch(plane.getTypeCode());
        String typeName = normalizeForMatch(plane.getTypeName());

        Long byTypeCode = resolveXpByTypeCode(typeCode);
        if (byTypeCode != null) {
            return byTypeCode;
        }

        Long byTypeName = resolveXpByTypeName(typeName);
        if (byTypeName != null) {
            return byTypeName;
        }

        return XP_TIER_1;
    }

    @Nullable
    private Long resolveXpByTypeName(@NonNull String typeName) {
        if (typeName.isEmpty()) {
            return null;
        }

        if (containsAny(typeName, "A380", "ANTONOV", "AN-124", "AN-225", "C-130", "C-17", "C-5", "A400")) {
            return XP_TIER_5;
        }

        if (containsAny(typeName, "777", "A350", "747")) {
            return XP_TIER_4;
        }

        if (containsAny(typeName, "787", "767", "A330", "A340")) {
            return XP_TIER_3;
        }

        if (containsAny(typeName, "A319", "A220", "E190", "E195", "EMBRAER", "ATR", "CRJ", "DASH 8", "Q400")) {
            return XP_TIER_2;
        }

        if (containsAny(typeName, "A320", "A321", "737")) {
            return XP_TIER_1;
        }

        return null;
    }

    @Nullable
    private Long resolveXpByTypeCode(@NonNull String typeCode) {
        if (typeCode.isEmpty()) {
            return null;
        }

        if (startsWithAny(typeCode, "A388")) return XP_TIER_5;
        if (startsWithAny(typeCode, "AN12", "AN22", "AN24", "AN72", "AN124", "AN225")) return XP_TIER_5;
        if (startsWithAny(typeCode, "C130", "C17", "C5", "E3", "A400")) return XP_TIER_5;

        if (startsWithAny(typeCode, "B77", "A35", "B744", "B748", "B74")) return XP_TIER_4;

        if (startsWithAny(typeCode, "B78", "B76", "A33", "A34")) return XP_TIER_3;

        if (startsWithAny(typeCode, "A319", "A220", "E190", "E195", "AT72", "AT75", "CRJ", "DH8")) {
            return XP_TIER_2;
        }

        if (startsWithAny(typeCode, "A320", "A321", "B737", "B738", "B739", "B73")) {
            return XP_TIER_1;
        }

        return null;
    }

    private boolean startsWithAny(@NonNull String source, @NonNull String... prefixes) {
        for (String prefix : prefixes) {
            if (source.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(@NonNull String source, @NonNull String... values) {
        for (String value : values) {
            if (source.contains(value)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String normalizeForMatch(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US);
    }

    @NonNull
    private String normalizeKeyPart(@Nullable String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toUpperCase(Locale.US)
                .replace("/", "_");
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long getLong(@NonNull DocumentSnapshot snap, @NonNull String field) {
        Long value = snap.getLong(field);
        return value == null ? 0L : value;
    }

}