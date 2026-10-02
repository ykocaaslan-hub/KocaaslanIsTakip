package com.kocaaslan.istakip;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public final class CloudSync {
    private CloudSync() {}

    public static boolean signedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public static String uid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public static void signIn(String email, String password, Result callback) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(t -> callback.done(t.isSuccessful(),
                        t.getException() == null ? null : t.getException().getMessage()));
    }

    public static void signOut() { FirebaseAuth.getInstance().signOut(); }

    public static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public interface Result { void done(boolean ok, String error); }
}
