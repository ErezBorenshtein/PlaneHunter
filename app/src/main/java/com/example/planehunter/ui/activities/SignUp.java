package com.example.planehunter.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.planehunter.R;
import com.example.planehunter.data.firebase.FirebaseHandler;

/**
 * Activity for user registration (Sign Up).
 * Collects user name, email, and password to create a new account and profile in Firebase.
 */
public class SignUp extends AppCompatActivity {

    /** Input field for the user's name. */
    private EditText etName;
    /** Input field for the user's email address. */
    private EditText etEmail;
    /** Input field for the user's password. */
    private EditText etPassword;
    /** Button to trigger the sign-up process. */
    private Button btnSignup;
    /** Link to navigate back to the login screen if the user already has an account. */
    private TextView tvHaveAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etSignupEmail);
        etPassword = findViewById(R.id.etSignupPassword);
        btnSignup = findViewById(R.id.btnSignup);
        tvHaveAccount = findViewById(R.id.tvHaveAccount);

        btnSignup.setOnClickListener(v -> doSignUp());

        tvHaveAccount.setOnClickListener(v -> finish());
    }

    /**
     * Validates registration data and creates a new user account and default profile in Firebase.
     */
    private void doSignUp() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(name) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password)) {

            Toast.makeText(this, "All fields required", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this,MainActivity.class);

        FirebaseHandler.getInstance()
                .signUpEmail(email, password)
                .continueWithTask(t ->
                        FirebaseHandler.getInstance().ensureDefaultProfile(name)
                )
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                    startActivity(intent);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Sign up failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
