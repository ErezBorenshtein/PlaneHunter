package com.example.planehunter.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.model.AircraftCategory;
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
import java.util.Locale;
import java.util.Set;

/**
 * Singleton class that handles all Firebase operations, including Authentication and Firestore.
 * Manages user profiles, leaderboard data, and aircraft capture logic.
 */
public class FirebaseHandler {

    /** Singleton instance. */
    private static volatile FirebaseHandler instance;

    /** Cooldown period for capturing the same aircraft (30 minutes). */
    private static final long CAPTURE_COOLDOWN_MS = 30L * 60L * 1000L;

    /** XP awarded for Tier 1 aircraft. */
    private static final long XP_TIER_1 = 1000L;
    /** XP awarded for Tier 2 aircraft. */
    private static final long XP_TIER_2 = 1300L;
    /** XP awarded for Tier 3 aircraft. */
    private static final long XP_TIER_3 = 1600L;
    /** XP awarded for Tier 4 aircraft. */
    private static final long XP_TIER_4 = 2000L;
    /** XP awarded for Tier 5 aircraft. */
    private static final long XP_TIER_5 = 2600L;

    /** Multiplier for the first time an aircraft is captured. */
    private static final double FIRST_TIME_MULTIPLIER = 2.0;
    /** Multiplier for repeat captures of the same aircraft. */
    private static final double REPEAT_MULTIPLIER = 0.3;

    /** Firebase Auth instance. */
    private final FirebaseAuth auth;
    /** Firebase Firestore instance. */
    private final FirebaseFirestore db;

    private FirebaseHandler() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Returns the singleton instance of FirebaseHandler.
     * @return The FirebaseHandler instance.
     */
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

    /**
     * Represents the result of an XP award operation for a plane capture.
     */
    public static class CaptureAwardResult {
        /** Whether XP was successfully awarded. */
        public boolean awarded;
        /** Whether this was the first time the aircraft was captured. */
        public boolean firstTime;
        /** Whether the capture failed due to an active cooldown. */
        public boolean cooldownActive;
        /** The amount of XP awarded in this operation. */
        public long xpAwarded;
        /** The remaining time in the cooldown in milliseconds. */
        public long cooldownRemainingMs;

        public CaptureAwardResult() {
        }
    }

    /**
     * Interface for listening to user profile changes.
     */
    public interface ProfileListener {
        void onProfile(@Nullable UserProfile profile);
        void onError(@NonNull Exception e);
    }

    /**
     * Interface for receiving the current user's rank on the leaderboard.
     */
    public interface MyRankListener {
        void onSuccess(@NonNull LeaderboardEntry entry, long rank);
        void onError(@NonNull Exception e);
    }

    /**
     * Checks if a user is currently signed in.
     * @return true if a user is signed in, false otherwise.
     */
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Gets the current user's UID or null if not signed in.
     * @return The UID string or null.
     */
    @Nullable
    public String getUidOrNull() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    /**
     * Gets the current user's UID or throws an exception if not signed in.
     * @return The UID string.
     * @throws IllegalStateException if the user is not signed in.
     */
    @NonNull
    public String getUidOrThrow() {
        String uid = getUidOrNull();
        if (uid == null) {
            throw new IllegalStateException("Not signed-in");
        }
        return uid;
    }

    /**
     * Signs up a new user with email and password.
     * @param email User email.
     * @param password User password.
     * @return A Task representing the sign-up operation.
     */
    public Task<AuthResult> signUpEmail(@NonNull String email, @NonNull String password) {
        return auth.createUserWithEmailAndPassword(email, password);
    }

    /**
     * Signs in an existing user with email and password.
     * @param email User email.
     * @param password User password.
     * @return A Task representing the sign-in operation.
     */
    public Task<AuthResult> signInEmail(@NonNull String email, @NonNull String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    /**
     * Signs out the current user.
     */
    public void signOut() {
        auth.signOut();
    }

    private DocumentReference myUserDoc() {
        return db.collection("users").document(getUidOrThrow());
    }

    private DocumentReference myLeaderboardDoc() {
        return db.collection("leaderboard").document(getUidOrThrow());
    }

    /**
     * Ensures that a default profile exists for the user. Creates one if it doesn't.
     * @param displayName The display name to use for a new profile.
     * @return A Task representing the operation.
     */
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

    /**
     * Retrieves the current user's profile.
     * @return A Task containing the UserProfile.
     */
    public Task<UserProfile> getMyProfile() {
        return myUserDoc().get().continueWith(task -> {
            DocumentSnapshot doc = task.getResult();

            if (doc == null || !doc.exists()) {
                return null;
            }

            return doc.toObject(UserProfile.class);
        });
    }

    /**
     * Updates the categories for which the user wants to receive alerts.
     * @param alertCategories List of category IDs.
     * @return A Task representing the operation.
     */
    public Task<Void> updateAlertCategories(@NonNull ArrayList<Long> alertCategories) {
        UserProfile profile = new UserProfile();
        profile.alertCategories = new ArrayList<>(alertCategories);
        profile.updatedAtMs = System.currentTimeMillis();

        return myUserDoc().set(profile, SetOptions.merge());
    }

    /**
     * Starts listening to changes in the current user's profile.
     * @param listener The listener to be notified of changes.
     * @return A ListenerRegistration that can be used to stop listening.
     */
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

    /**
     * Processes a plane capture, awarding XP if the cooldown has passed.
     * @param plane The plane being captured.
     * @return A Task containing the CaptureAwardResult.
     */
    public Task<CaptureAwardResult> awardCaptureXp(@NonNull Plane plane) {
        String uid = getUidOrThrow();
        long now = System.currentTimeMillis();

        String captureKey = buildCaptureKey(plane);
        long baseXp = resolveXpByCategory(plane.getCategory());

        return db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentReference captureRef = userRef.collection("captures").document(captureKey);
            DocumentReference leaderboardRef = db.collection("leaderboard").document(uid);

            DocumentSnapshot userSnap = transaction.get(userRef);
            DocumentSnapshot captureSnap = transaction.get(captureRef);

            UserProfile profile = buildProfile(userSnap, uid);
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

            long xpAwarded = firstTime ? (long)(baseXp * FIRST_TIME_MULTIPLIER)
                    : (long)(baseXp * REPEAT_MULTIPLIER);

            profile.xp += xpAwarded;
            profile.caughtCount += 1L;
            profile.updatedAtMs = now;

            if (firstTime) {
                profile.uniqueRegistrationsCount += 1L;
            }

            PlaneCapture capture = buildPlaneCaptureForFirebase(plane, now, captureSnap, firstTime);

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

    /**
     * Converts a DocumentSnapshot to a LeaderboardEntry.
     * @param doc The DocumentSnapshot.
     * @return The LeaderboardEntry.
     */
    @Nullable
    public LeaderboardEntry toLeaderboardEntry(@NonNull DocumentSnapshot doc) {
        LeaderboardEntry entry = doc.toObject(LeaderboardEntry.class);

        if (entry == null) {
            return null;
        }

        if (entry.name == null || entry.name.trim().isEmpty()) {
            entry.name = "Player";
        }

        return entry;
    }


    /**
     * Retrieves the current user's rank.
     * @param listener The listener.
     */
    public void getMyLeaderboardRank(@NotNull MyRankListener listener){
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

                   long xp =0;

                   if(doc.getLong("xp") !=null){
                        xp = doc.getLong("xp");
                   };

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

    /**
     * Retrieves planes in cooldown.
     * @return A set of ICAO24 strings.
     */
    public Task<Set<String>> getMyPlanesInCooldown() {
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

    /**
     * Gets top leaderboard entries.
     * @param limit The limit.
     * @return The query.
     */
    public Query getLeaderboardTop(int limit) {
        return db.collection("leaderboard")
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(limit);
    }

    private UserProfile buildProfile(@NonNull DocumentSnapshot userSnap, @NonNull String uid) {
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

    private PlaneCapture buildPlaneCaptureForFirebase(
            @NonNull Plane plane,
            long now,
            @NonNull DocumentSnapshot existingCaptureSnap,
            boolean firstTime
    ) {
        if (firstTime) {
            return new PlaneCapture(
                    convEmptyToNull(plane.getRegistration()),
                    convEmptyToNull(plane.getIcao24()),
                    convEmptyToNull(plane.getTypeCode()),
                    convEmptyToNull(plane.getTypeName()),
                    convEmptyToNull(plane.getCallSign()),
                    now
            );
        }

        PlaneCapture capture = existingCaptureSnap.toObject(PlaneCapture.class);
        if (capture == null) {
            return new PlaneCapture(
                    convEmptyToNull(plane.getRegistration()),
                    convEmptyToNull(plane.getIcao24()),
                    convEmptyToNull(plane.getTypeCode()),
                    convEmptyToNull(plane.getTypeName()),
                    convEmptyToNull(plane.getCallSign()),
                    now
            );
        }


        String reg = plane.getRegistration();
        if (reg != null && !reg.trim().isEmpty()) {
            capture.registration = reg.trim();
        }
        String icao = plane.getIcao24();
        if (icao != null && !icao.trim().isEmpty()) {
            capture.icao24 = icao.trim();
        }
        String typeCode = plane.getTypeCode();
        if (typeCode != null && !typeCode.trim().isEmpty()) {
            capture.typeCode = typeCode.trim();
        }

        String typeName = plane.getTypeName();
        if (typeName != null && !typeName.trim().isEmpty()) {
            capture.typeName = typeName.trim();
        }

        String callSign = plane.getCallSign();
        if (callSign != null && !callSign.trim().isEmpty()) {
            capture.callSign = callSign.trim();
        }

        if (capture.firstCaughtAtMs <= 0L) {
            capture.firstCaughtAtMs = now;
        }

        capture.lastCaughtAtMs = now;
        capture.timesCaught = Math.max(1L, capture.timesCaught + 1L);

        return capture;
    }

    private LeaderboardEntry buildLeaderboardEntry(@NonNull String uid,@NonNull String name, long xp, long captures) {
        return new LeaderboardEntry(uid,name,xp,captures);
    }

    private String buildCaptureKey(@NonNull Plane plane) {
        return "ICAO_" + normalizeICAO(plane.getIcao24());
    }

    private Long resolveXpByCategory(long category) {
        if (category == AircraftCategory.HELICOPTER) {
            return XP_TIER_3;
        }

        if (category == AircraftCategory.MILITARY_GOVERNMENT) {
            return XP_TIER_5;
        }

        if (category == AircraftCategory.BUSINESS_JET) {
            return XP_TIER_4;
        }

        if (category == AircraftCategory.CARGO) {
            return XP_TIER_4;
        }

        if (category == AircraftCategory.AIRLINER) {
            return XP_TIER_2;
        }

        if (category == AircraftCategory.TURBOPROP_REGIONAL) {
            return XP_TIER_2;
        }

        if (category == AircraftCategory.GENERAL_AVIATION) {
            return XP_TIER_1;
        }

        return XP_TIER_1;
    }


    @NonNull
    private String normalizeICAO(@Nullable String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toUpperCase(Locale.US)
                .replace("/", "_");
    }

    @Nullable
    private String convEmptyToNull(@Nullable String value) {
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