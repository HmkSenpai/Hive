package com.example.hive;

import android.os.Bundle;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private int count = 0;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        countText = findViewById(R.id.countText);
        MaterialButton incrementButton = findViewById(R.id.incrementButton);
        MaterialButton resetButton = findViewById(R.id.resetButton);

        incrementButton.setOnClickListener(v -> {
            count++;
            countText.setText(getString(R.string.test_count, count));
        });

        resetButton.setOnClickListener(v -> {
            count = 0;
            countText.setText(getString(R.string.test_count, count));
        });
    }
}
