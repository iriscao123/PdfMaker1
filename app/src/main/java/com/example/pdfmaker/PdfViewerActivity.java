package com.example.pdfmaker;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {

    private ImageView returnBn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        returnBn = findViewById(R.id.returnBn);
        PDFView pdfView = findViewById(R.id.pdfView);

//
        returnBn.setOnClickListener(view -> finish()); // 返回上一层界面

        String pdfPath = getIntent().getStringExtra("pdfPath");


        if (pdfPath == null || pdfPath.isEmpty()) {
            Log.e("PdfViewerActivity", "找不到PDF 文件的路径");
            return;
        }
        File file = new File(pdfPath);
        if (!file.exists()) {
            Log.e("PdfViewerActivity", "PDF 文件不存在");
            return;
        }
        pdfView.fromFile(file).load();
    }
}

