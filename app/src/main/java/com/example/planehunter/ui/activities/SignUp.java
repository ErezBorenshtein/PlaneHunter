package com.example.planehunter.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.planehunter.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;


public class SignUp extends AppCompatActivity {

    EditText etName,etSignupEmail,etSignupPassword;
    Button btnSignup;
    TextView tvHaveAccount;

    FirebaseFirestore DB = FirebaseFirestore.getInstance();

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        etName = findViewById(R.id.etName);
        etSignupEmail = findViewById(R.id.etSignupEmail);
        etSignupPassword = findViewById(R.id.etSignupPassword);
        btnSignup = findViewById(R.id.btnSignup);
        tvHaveAccount = findViewById(R.id.tvHaveAccount);

        btnSignup.setOnClickListener(v -> {
            signUp();
        });

        tvHaveAccount.setOnClickListener(view -> {
            Intent intent = new Intent(SignUp.this, LogIn.class);
            startActivity(intent);
            finish();
        });
    }

    public void signUp() {
        String name = etName.getText().toString();
        String email = etSignupEmail.getText().toString();
        String password = etSignupPassword.getText().toString();

        HashMap<String, Object> user = new HashMap<>();
        user.put("name", name);
        if(name.isEmpty() || email.isEmpty() || password.isEmpty()){
            Toast.makeText(this, "name or email or password is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Intent intent = new Intent(SignUp.this, MainActivity.class);
                    //intent.putExtra("name", name);
                    Toast.makeText(this, auth.getUid(), Toast.LENGTH_SHORT).show();
                    DB.collection("Notes")
                            .document(auth.getUid())
                            .set(user)
                            .addOnSuccessListener(unused -> {
                                Log.d("Erezs","User created successfully");
                                Toast.makeText(this, "User created successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e->{
                                Log.d("Erezs","Somthing went wrong");
                            });

                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e->{
                    Log.d("E","Somthing went wrong2");
                });
    }
}