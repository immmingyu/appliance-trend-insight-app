package com.example.gajeontiming;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.scan.ModelNameExtractor;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;

/**
 * F-16 모델명 스캔 화면.
 * 가격표 · 제품 라벨을 찍거나(기본 카메라 앱) 갤러리에서 고르면,
 * ML Kit이 기기 안에서 글자를 읽고(인터넷 · 서버 전송 없음) 모델명 후보를 보여준다.
 * 후보를 고르거나 직접 고친 뒤 검색하면 제품 목록 화면으로 넘어간다.
 */
public class ScanActivity extends AppCompatActivity {

    private ImageView photo;
    private TextView guide, stateText;
    private ProgressBar progress;
    private View resultBox;
    private ChipGroup candidateGroup;
    private EditText queryInput;

    private Uri photoUri;            // 카메라 앱이 찍은 사진을 저장할 위치
    private String detectedCategory; // 사진 속 글자에서 찾은 품목 (예: "에어컨")
    private TextRecognizer recognizer;

    /** 기본 카메라 앱으로 찍기 → photoUri에 원본 크기로 저장된다 */
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            saved -> {
                if (Boolean.TRUE.equals(saved)) recognize(photoUri);
            });

    /** 갤러리에서 고르기 (에뮬레이터 테스트용으로도 쓴다) */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) recognize(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.scan_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        photo = findViewById(R.id.scan_photo);
        guide = findViewById(R.id.scan_guide);
        stateText = findViewById(R.id.scan_state);
        progress = findViewById(R.id.scan_progress);
        resultBox = findViewById(R.id.scan_result);
        candidateGroup = findViewById(R.id.scan_candidates);
        queryInput = findViewById(R.id.scan_query);

        // 한국어 인식 모델은 영문 · 숫자도 함께 읽는다 (가격표에 한글과 모델명이 섞여 있어서 이걸 쓴다)
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

        findViewById(R.id.scan_camera).setOnClickListener(v -> openCamera());
        findViewById(R.id.scan_gallery).setOnClickListener(v -> pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        findViewById(R.id.scan_search).setOnClickListener(v -> search());

        resultBox.setVisibility(View.GONE);
    }

    // ── 사진 받기 ─────────────────────────────────────────────

    private void openCamera() {
        try {
            // 앱 전용 임시 폴더에 빈 파일을 만들고, 카메라 앱이 그 파일에 쓰도록 주소를 넘긴다
            File dir = new File(getCacheDir(), "scan");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("폴더를 만들 수 없음");
            File file = File.createTempFile("label_", ".jpg", dir);
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            takePicture.launch(photoUri);
        } catch (Exception e) {
            Toast.makeText(this, "카메라를 열 수 없어요. 갤러리에서 사진을 골라 주세요", Toast.LENGTH_SHORT).show();
        }
    }

    // ── 글자 인식 ─────────────────────────────────────────────

    private void recognize(Uri uri) {
        photo.setImageURI(uri);
        photo.setVisibility(View.VISIBLE);
        guide.setVisibility(View.GONE);
        resultBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.scan_reading);

        InputImage image;
        try {
            image = InputImage.fromFilePath(this, uri);   // 사진 방향(세로 · 가로)도 알아서 맞춘다
        } catch (IOException e) {
            showMessage(getString(R.string.scan_read_failed));
            return;
        }

        recognizer.process(image)
                .addOnSuccessListener(text -> showCandidates(text.getText()))
                .addOnFailureListener(e -> showMessage(getString(R.string.scan_read_failed)));
    }

    private void showCandidates(String recognizedText) {
        progress.setVisibility(View.GONE);
        ModelNameExtractor.Result result = ModelNameExtractor.extract(recognizedText);
        detectedCategory = result.category;

        candidateGroup.removeAllViews();
        resultBox.setVisibility(View.VISIBLE);

        if (result.candidates.isEmpty()) {
            stateText.setText(recognizedText.trim().isEmpty()
                    ? getString(R.string.scan_no_text)
                    : getString(R.string.scan_no_model));
            queryInput.setText(detectedCategory != null ? detectedCategory : "");
            return;
        }

        stateText.setText(detectedCategory != null
                ? getString(R.string.scan_found_with_category, detectedCategory)
                : getString(R.string.scan_found));

        // 후보를 칩으로. 누르면 검색창에 들어간다. 첫 번째(가장 모델명다운 것)를 미리 넣어 둔다
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String candidate : result.candidates) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, candidateGroup, false);
            chip.setText(candidate);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                queryInput.setText(candidate);
                queryInput.setSelection(candidate.length());
            });
            candidateGroup.addView(chip);
        }
        queryInput.setText(result.candidates.get(0));
        queryInput.setSelection(queryInput.length());
    }

    private void showMessage(String text) {
        progress.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
    }

    // ── 검색으로 넘기기 ───────────────────────────────────────

    private void search() {
        String query = queryInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "검색할 모델명을 입력해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        // 사진에서 품목을 찾았으면 같이 넘겨서 소모품이 섞이지 않게 한다
        String category = detectedCategory != null ? detectedCategory : Categories.detect(query);
        Intent intent = new Intent(this, ProductListActivity.class);
        intent.putExtra(ProductListActivity.EXTRA_QUERY, query);
        intent.putExtra(ProductListActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }
}
