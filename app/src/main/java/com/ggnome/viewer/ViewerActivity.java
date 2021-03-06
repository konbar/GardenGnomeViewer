package com.ggnome.viewer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.ggnome.viewer.common.SettingsManager;
import com.ggnome.viewer.common.StoragePathHandler;
import com.ggnome.viewer.databinding.ActivityViewerBinding;
import com.ggnome.viewer.helper.GardenGnomePackage;
import com.ggnome.viewer.task.PackageLoaderTask;

import java.io.File;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.webkit.WebViewAssetLoader;

public class ViewerActivity extends AppCompatActivity {

    public static String EXTRA_ENABLE_BACKWARDS_NAVIGATION = "EXTRA_ENABLE_BACKWARDS_NAVIGATION";
    public static String EXTRA_FILE_NAME = "EXTRA_FILE_NAME";
    public static String EXTRA_MIME_TYPE = "EXTRA_MIME_TYPE";

    private ActivityViewerBinding activityViewerBinding;

    private PackageLoaderTask packageLoaderTask;
    private GardenGnomePackage gardenGnomePackage;
    private boolean isFullscreenMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.activityViewerBinding = DataBindingUtil.setContentView(this, R.layout.activity_viewer);

        if(this.getIntent() != null) {
            // enable backwards navigation only when started from application context
            if(this.getIntent().getBooleanExtra(EXTRA_ENABLE_BACKWARDS_NAVIGATION, false)) {
                if(this.getSupportActionBar() != null) {
                    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                }
            }

            // load package on first activity creation
            Uri fileUri = Uri.fromFile(new File(this.getIntent().getStringExtra(EXTRA_FILE_NAME)));
            this.loadPackageFile(fileUri);
        }

        // init web view component with local asset loader
        final WebViewAssetLoader webViewAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/ggpkg/", new StoragePathHandler(this.getDir("ggpkg", Context.MODE_PRIVATE).getAbsolutePath()))
                .build();

        this.activityViewerBinding.viewerPanel.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return webViewAssetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        this.activityViewerBinding.viewerPanel.getSettings().setJavaScriptEnabled(true);

        this.showLoadingPanel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // load package file when new intent is set to activity
        if(this.getIntent() != null) {
            // enable backwards navigation only when started from application context
            if(this.getIntent().getBooleanExtra(EXTRA_ENABLE_BACKWARDS_NAVIGATION, false)) {
                if(this.getSupportActionBar() != null) {
                    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                }
            }

            // load package on first activity creation
            Uri fileUri = Uri.fromFile(new File(this.getIntent().getStringExtra(EXTRA_FILE_NAME)));
            this.loadPackageFile(fileUri);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(this.packageLoaderTask != null && (this.packageLoaderTask.getStatus() == AsyncTask.Status.RUNNING || this.packageLoaderTask.getStatus() == AsyncTask.Status.PENDING)) {
            this.packageLoaderTask.cancel(true);
        }

        if(this.gardenGnomePackage != null) {
            this.gardenGnomePackage.close();
        }
    }

    @Override
    public void onBackPressed() {
        if(this.isFullscreenMode) {
            this.leaveFullscreenMode();
        } else {
            this.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.menu_viewer, menu);

        // enable / disable menu items depending on settings
        SettingsManager settingsManager = SettingsManager.getInstance(this);

        MenuItem fullscreenMenuItem = menu.findItem(R.id.menuViewerFullscreen);
        if(fullscreenMenuItem != null) {
            fullscreenMenuItem.setVisible(settingsManager.getFullscreenEnabled());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                this.finish();
                break;

            case R.id.menuViewerFullscreen:
                this.enterFullscreenMode();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if(hasFocus && this.isFullscreenMode) {
            this.enterFullscreenMode();
        }
    }

    /**
     * Display loading panel.
     */
    private void showLoadingPanel() {
        this.activityViewerBinding.loadingPanel.setVisibility(View.VISIBLE);
        this.activityViewerBinding.viewerPanel.setVisibility(View.GONE);
    }

    /**
     * Display web viewer panel.
     */
    private void showViewerPanel() {
        this.activityViewerBinding.viewerPanel.loadUrl("https://appassets.androidplatform.net/ggpkg/index.html");

        this.activityViewerBinding.loadingPanel.setVisibility(View.GONE);
        this.activityViewerBinding.viewerPanel.setVisibility(View.VISIBLE);
    }

    /**
     * Display a simple error alert.
     */
    private void showErrorAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.activity_viewer_package_error);
        builder.setPositiveButton(R.string.str_back, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        builder.show();
    }

    /**
     * Enters the activity immersive fullscreen mode.
     */
    private void enterFullscreenMode() {
        View decorView = this.getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // set content to appear below status bar
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // hide the nav bar and status bar
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        // display user info
        Toast.makeText(this, R.string.activity_viewer_package_fullscreen_toast, Toast.LENGTH_LONG).show();

        // keep layout resizing for system bars
        this.activityViewerBinding.fitSystemWindowsLayout.setFit(false);
        this.isFullscreenMode = true;
    }

    /**
     * Leaves the activity immersive fullscreen mode.
     */
    private void leaveFullscreenMode() {
        View decorView = this.getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_VISIBLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        // keep layout resizing for system bars
        this.activityViewerBinding.fitSystemWindowsLayout.setFit(true);
        this.isFullscreenMode = false;
    }

    /**
     * Loads an GGPKG file based on a URI.
     *
     * @param uri The URI of the file to load.
     */
    private void loadPackageFile(Uri uri) {
        String packageFileName = null;
        if(uri != null && uri.getScheme().equals("file")) {
            packageFileName = uri.getPath();
        } else if(uri != null && uri.getScheme().equals("content")) {
            // transform content uri into file uri
            Cursor cursor = this.getContentResolver().query(uri, new String[] { MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME }, null, null, null);
            if(cursor.moveToFirst()) {
                if(cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)).endsWith(".ggpkg")) {
                    packageFileName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                }
            }
            cursor.close();
        }

        if(packageFileName != null) {
            this.packageLoaderTask = new PackageLoaderTask(this);
            this.packageLoaderTask.setLoaderTaskListener(new PackageLoaderTask.PackageLoaderTaskListener() {
                @Override
                public void onPackageLoaded(GardenGnomePackage packageObject) {
                    gardenGnomePackage = packageObject;

                    if(gardenGnomePackage != null) {
                        showViewerPanel();
                    } else {
                        showErrorAlert();
                    }
                }

                @Override
                public void onPackageProgressUpdate(int progressValue) {
                    updateProgress(progressValue);
                }
            });

            this.packageLoaderTask.execute(packageFileName);
        } else {
            this.showErrorAlert();
        }
    }

    /**
     * Updates the progress in progress bar and text view.
     *
     * @param progress The current progress to display.
     */
    private void updateProgress(int progress) {
        String updateText = this.getResources().getString(R.string.str_percent, progress);
        this.activityViewerBinding.lblLoadingProgress.setText(updateText);
        this.activityViewerBinding.pgbLoading.setProgress(progress);
    }

}
