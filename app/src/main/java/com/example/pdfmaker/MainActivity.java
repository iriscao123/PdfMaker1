package com.example.pdfmaker;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity<val> extends AppCompatActivity {
        private ListView pdfListView;
        private ArrayAdapter<String> adapter;
        private List<String> pdfFileNames;
        private File pdfDir;
        private ImageView returnBn,takePhotoButton,selectImagesButton;

        private static final int REQUEST_IMAGE_CAPTURE = 1;
        private static final int REQUEST_SELECT_IMAGES = 2;

        private List<Uri> imageUris = new ArrayList<>();
        private Uri currentPhotoUri;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
//            returnBn = findViewById(R.id.returnBn);


            takePhotoButton = findViewById(R.id.cameraButton);
            selectImagesButton = findViewById(R.id.selectImagesButton);
            pdfListView = findViewById(R.id.pdfListView);

            // 初始化 PDF 文件目录
            pdfDir = new File(getExternalFilesDir(null), "GeneratedPDFs");
            if (!pdfDir.exists()) {
                pdfDir.mkdirs();
            }

            // 初始化 ListView 和适配器
            pdfFileNames = new ArrayList<>();
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, pdfFileNames);
            pdfListView.setAdapter(adapter);
//返回键监听

//            returnBn.setOnClickListener(view -> finish()); // 返回上一层界面

//相机和相册监听
            takePhotoButton.setOnClickListener(v -> takePhoto());
            selectImagesButton.setOnClickListener(v -> selectImages());

 // 点击列表项监听
            pdfListView.setOnItemClickListener((parent, view, position, id) -> openPdfFile(position));

  // 长按列表项监听
            pdfListView.setOnItemLongClickListener((parent, view, position, id) -> {
                // 长按弹出重命名或删除选项
                File selectedFile = new File(pdfDir, pdfFileNames.get(position));
                showFileOptionsDialog(selectedFile, position);
                return true;
            });

// 显示当前文件列表
            refreshPdfList();
//相机权限请求
//            requestPermissions();

        }

// 刷新 PDF 文件列表
        private void refreshPdfList() {
            pdfFileNames.clear();

            if (pdfDir.exists()) {
                File[] files = pdfDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".pdf")) {
                            pdfFileNames.add(file.getName());
                        }
                    }
                }
            }

            adapter.notifyDataSetChanged();
        }


 // 生成 PDF 文件（优化避免耗时太多）
    //        优化方案：将耗时操作移到后台线程
    //1.避免使用toast命令
    //2.使用 AsyncTask（过时）或 ExecutorService（推荐）处理耗时任务。在任务完成后，再切换回主线程更新UI。
    //3. 处理图片文件时释放内存
    //
    //优化代码中，增加了对 Bitmap 的回收（bitmap.recycle() 和 scaledBitmap.recycle()），避免大图片操作导致内存泄漏或 OOM（Out Of Memory）异常。
//

        private void generatePdfFromImages(List<Uri> imageUris) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(() -> {
                PdfDocument pdfDocument = new PdfDocument();
                FileOutputStream fos = null;

                try {
    // 生成唯一文件名
                    File pdfFile = getUniqueFileName("GeneratedPDF");
                    fos = new FileOutputStream(pdfFile);

                    for (int i = 0; i < imageUris.size(); i++) {
                        Uri uri = imageUris.get(i);
                        InputStream inputStream = null;
                        Bitmap bitmap = null;

                        try {
    // 读取图片
                            inputStream = getContentResolver().openInputStream(uri);
                            bitmap = BitmapFactory.decodeStream(inputStream);
                            if (bitmap == null) {
                                throw new IOException("无法解析图片 URI：" + uri.toString());
                            }

    // 动态调整图片尺寸适配 A4 (595 x 842) 页面大小
                            int pageWidth = 595;
                            int pageHeight = 842;
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                                    bitmap,
                                    pageWidth,
                                    (int) ((float) bitmap.getHeight() / bitmap.getWidth() * pageWidth),
                                    true
                            );

    // 创建 PDF 页面
                            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                            PdfDocument.Page page = pdfDocument.startPage(pageInfo);

    // 居中绘制图片
                            Canvas canvas = page.getCanvas();
                            int xOffset = (pageWidth - scaledBitmap.getWidth()) / 2;
                            int yOffset = (pageHeight - scaledBitmap.getHeight()) / 2;
                            canvas.drawBitmap(scaledBitmap, xOffset, yOffset, null);

                            pdfDocument.finishPage(page);

    // 释放 Bitmap 资源
                            scaledBitmap.recycle();
                        } finally {
    // 关闭 InputStream 和释放 Bitmap
                            if (bitmap != null) {
                                bitmap.recycle();
                            }
                            if (inputStream != null) {
                                inputStream.close();
                            }
                        }
                    }

    // 写入 PDF 文件
                    pdfDocument.writeTo(fos);

    // 在主线程更新 UI
                    File finalPdfFile = pdfFile;
                    handler.post(() -> {
                        Toast.makeText(this, "PDF 已生成：" + finalPdfFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        refreshPdfList(); // 可选：更新 UI 方法
                    });

                } catch (Exception e) {
                    Log.e("PDF", "生成 PDF 文件失败：" + e.getMessage(), e);
                    handler.post(() -> Toast.makeText(this, "生成失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                } finally {
                    try {
    // 关闭资源
                        if (fos != null) {
                            fos.close();
                        }
                        pdfDocument.close();
                    } catch (IOException e) {
                        Log.e("PDF", "关闭资源失败：" + e.getMessage(), e);
                    }
                }
            });
        }

//相机使用权限许可
        private void requestPermissions() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
    //在AndriodManifest.xml定义权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        100);
            }
        }
//  检查相机权限回调：
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == 100) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(this, "请授予相机和存储权限", Toast.LENGTH_SHORT).show();
                }
            }
        }

//打开相机拍照
        private void takePhoto() {
            //相机权限请求
            requestPermissions();
    // 创建用于保存图片的文件
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
    // 文件创建异常处理
                ex.printStackTrace();
            }
    // 确保文件创建成功
            if (photoFile != null) {
//                Uri photoURI = FileProvider.getUriForFile(this,
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".file-provider",
                        photoFile);
//在AndriodManifest.xml定义权限
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                try {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                } catch (ActivityNotFoundException e) {
    // 没有找到相机应用
                    Toast.makeText(this, "未找到可用的相机应用", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "无法创建图片文件", Toast.LENGTH_SHORT).show();
            }
        }


//拍照生成图片
        private File createImageFile() throws IOException {
    // 生成唯一的图片文件名
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";

    // 获取图片存储目录
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

    // 创建文件
            File image = File.createTempFile(
                    imageFileName, // 文件前缀
                    ".jpg", // 文件后缀
                    storageDir // 文件目录
            );
            return image;
        }

 // 选择图片功能
        private void selectImages() {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQUEST_SELECT_IMAGES);
        }


 // 拍照或选择图片的回调
        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (resultCode == RESULT_OK) {
//                if (requestCode == REQUEST_IMAGE_CAPTURE) {
                if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
    // 拍照后保存图片 URI
                    imageUris.add(currentPhotoUri);
                    generatePdfFromImages(imageUris);
                    Toast.makeText(this, "照片已成功拍摄！", Toast.LENGTH_SHORT).show();
                } else if (requestCode == REQUEST_SELECT_IMAGES && data != null) {
    // 从图库选择图片
                    if (data.getClipData() != null) {
    // 多选图片
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri imageUri = data.getClipData().getItemAt(i).getUri();
                            imageUris.add(imageUri);
                        }
                    } else if (data.getData() != null) {
    // 单选图片
                        Uri imageUri = data.getData();
                        imageUris.add(imageUri);
                    }
                    generatePdfFromImages(imageUris);
                }
            }
        }


 //  打开 PDF 文件
        private void openPdfFile(int position) {
            File pdfDir = new File(getExternalFilesDir(null), "GeneratedPDFs");
            if (!pdfDir.exists() || pdfFileNames == null || position >= pdfFileNames.size()) {
                Log.e("OpenPdfFile", "Invalid position or directory");
                return;
            }
            File selectedFile = new File(pdfDir, pdfFileNames.get(position));
            if (!selectedFile.exists()) {
                Log.e("OpenPdfFile", "File does not exist: " + selectedFile.getAbsolutePath());
                return;
            }
            Intent intent = new Intent(this, PdfViewerActivity.class);
            intent.putExtra("pdfPath", selectedFile.getAbsolutePath());
            startActivity(intent);
        }

// 显示文件处理选项
            private void showFileOptionsDialog(File file, int position) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("选择操作");
                builder.setItems(new CharSequence[]{"重命名", "删除","分享"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // 重命名
                            showRenameDialog(file, position);
                            break;
                        case 1: // 删除
                            deleteFile(file, position);
                            break;
                        case 2: // 分享
                            if (file == null || !file.exists()) {
                                Toast.makeText(this, "文件不存在，无法分享", Toast.LENGTH_SHORT).show();
                                break;
                            }
                            sharePdf(file); // 调用分享功能
                            break;
                    }
                });
                builder.show();
            }


// 修改 PDF 文件名
        private void showRenameDialog(File file, int position) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("重命名文件");

    // 输入框
            final EditText input = new EditText(this);
            input.setText(file.getName());
            builder.setView(input);

    // 确定按钮
            builder.setPositiveButton("确定", (dialog, which) -> {
                String newFileName = input.getText().toString().trim();

    // 检查是否为空或重复
                if (newFileName.isEmpty() || newFileName.equals(file.getName())) {
                    Toast.makeText(this, "文件名无效", Toast.LENGTH_SHORT).show();
                    return;
                }

                File newFile = new File(pdfDir, newFileName);
                if (newFile.exists()) {
                    Toast.makeText(this, "文件名已存在", Toast.LENGTH_SHORT).show();
                    return;
                }

    // 重命名文件
                if (file.renameTo(newFile)) {
                    pdfFileNames.set(position, newFileName); // 更新文件列表
                    adapter.notifyDataSetChanged(); // 刷新列表
                    Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            });

    // 取消按钮
            builder.setNegativeButton("取消", null);
            builder.show();
        }


//   删除文件
        private void deleteFile(File file, int position) {
    // 确认删除对话框
            new AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除该文件吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        if (file.delete()) {
                            pdfFileNames.remove(position); // 从列表中移除
                            adapter.notifyDataSetChanged(); // 刷新列表
                            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }


//        确保文件名唯一性时，可以用以下方法生成：
        private File getUniqueFileName(String baseName) {
            File file;
            int counter = 0;
            do {
                String fileName = baseName + (counter == 0 ? "" : "_" + counter) + ".pdf";
                file = new File(pdfDir, fileName);
                counter++;
            } while (file.exists());
            return file;
        }

    private void sharePdf(File pdfFile) {
// 使用 FileProvider 获取共享 URI
        Uri pdfUri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".fileprovider",
                pdfFile
        );

// 配置分享 Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

// 显示分享选择器
        startActivity(Intent.createChooser(shareIntent, "分享 PDF 文件"));
    }


    }

//    功能说明
//
//            1. 实时更新文件列表：
//            • refreshPdfList() 方法扫描 PDF 目录，读取所有文件并更新到 ListView。
//            2. 点击打开文件：
//            • 用户点击 ListView 的任意项，会调用 openPdfFile() 打开对应的 PDF。
//            3. 新增文件时更新：
//            • 每次调用 generateSamplePdf() 方法生成 PDF 后，调用 refreshPdfList() 刷新列表。
//            4. PDF 文件目录：
//            • 所有生成的 PDF 文件都存储在：
//
//            /storage/emulated/0/Android/data/[包名]/files/GeneratedPDFs/
//


//核心改进点
//    1. 后台线程执行耗时任务：
//            • 使用 ExecutorService 将耗时操作从主线程中剥离。
//            2. 主线程更新UI：
//            • 通过 Handler 切换回主线程更新 ListView 和显示 Toast。
//            3. 内存优化：
//            • 在处理每张图片后主动回收 Bitmap，避免占用大量内存。
//
//            最终效果
//
//            • 主线程不会卡顿，ANR 问题解决。
//            • PDF 生成完毕后，文件列表会及时更新，并通过 Toast 通知用户操作成功。