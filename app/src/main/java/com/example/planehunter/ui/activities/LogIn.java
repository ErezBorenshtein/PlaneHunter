package com.example.planehunter.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.planehunter.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;


public class LogIn extends AppCompatActivity {

    EditText etEmail,etPassword;
    Button btnLogIn;
    TextView tvNoAccount;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private FirebaseFirestore DB = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_log_in);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogIn = findViewById(R.id.btnLogin);
        tvNoAccount = findViewById(R.id.tvNoAccount);

        auth.signOut();

        if(auth.getCurrentUser() != null){
            Intent intent = new Intent(LogIn.this, MainActivity.class);
            startActivity(intent);
            finish();
        }

        btnLogIn.setOnClickListener(v -> {
            logIn();
        });

        tvNoAccount.setOnClickListener(view -> {
            Intent intent = new Intent(LogIn.this, SignUp.class);
            startActivity(intent);
            finish();
        });



    }
    public void logIn() {
        String email = etEmail.getText().toString();
        String password = etPassword.getText().toString();

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Intent intent = new Intent(LogIn.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LogIn.this, "Log in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


}