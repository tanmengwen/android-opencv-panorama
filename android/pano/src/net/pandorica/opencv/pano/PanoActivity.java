/*
 * Copyright (C) 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package net.pandorica.opencv.pano;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Gallery;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ViewSwitcher.ViewFactory;

public class PanoActivity extends Activity implements ViewFactory, OnClickListener {
    public static final String SETTINGS                = "Pano_Settings";

    // persistent settings key's
    private final String SETTINGS_SAVE_PATH            = "path";
    private final String SETTINGS_IMAGE_PREFIX         = "image";
    private final String SETTINGS_OUTPUT_IMAGE         = "output";
    private final String SETTINGS_WARP_TYPE            = "warp";
    private final String SETTINGS_MATCH_CONF           = "match_conf";
    private final String SETTINGS_CONF_THRESH          = "conf_thresh";
    private final String SETTINGS_SHOW_TIP             = "show_tip";

    // default settings
    private String mDefaultPath;
    private String mDefaultImagePrefix;
    private String mDefaultOutputName                  = "result.jpg";
    private String mDefaultWarpType                    = "spherical";
    private String mDefaultMatchConf                   = "0.5";
    private String mDefaultConfThresh                  = "0.8";
    private boolean mDefaultShowTip                    = true;

    // possible dialogs to open
    public static final int DIALOG_RESULTS             = 0;
    public static final int DIALOG_STITCHING           = 1;
    public static final int DIALOG_FEATURE_COMPARISON  = 2;
    public static final int DIALOG_FEATURE_ALPHA       = 3;
    public static final int DIALOG_SUCCESS             = 4;
    public static final int DIALOG_ERROR               = 5;
    public static final int DIALOG_CONTEXT_MENU        = 6;

    // runtime setting
    private SharedPreferences mSettings;
    private String mDirPath;
    private String mImagePrefix;
    private String mOutputImage;
    private String mWarpType;
    private String mMatchConf;
    private String mConfThresh;
    private String mSubDir = null;
    private boolean mShowTip;

    private String mType                               = ".jpg";
    private String smallType                           = ".png";
    public static final String MIME_TYPE               = "image/jpg";
    private int mCurrentImage                          = 1;
    private int mGalleryImage                          = 0;
    private int mGalleryLongClick                      = 0;
    private Gallery mGallery;
    private ImageSwitcher mImageSwitcher;
    private PanoAdapter mPanoAdapter;
    private List<File> mDirectories;

    private Button mShareButton;
    private Button mRestitchButton;

    /**
     * Called when activity is first created.
     * Initializes the default storage locations
     * Initializes the ImageSwitcher and Button visibility
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Initialize Dynamic Defaults
        mDefaultPath = Environment.getExternalStorageDirectory().toString()
                + "/" + getResources().getString(R.string.default_folder) + "/";
        mDefaultImagePrefix = getResources().getString(R.string.default_prefix);

        // Initialize Our Settings
        loadSettings(SETTINGS);

        setContentView(R.layout.main);

        // Initialize the gallery view
        mPanoAdapter = new PanoAdapter(this);
        mImageSwitcher = (ImageSwitcher) findViewById(R.id.main_switcher);
        mImageSwitcher.setFactory(this);

        mGallery = (Gallery) findViewById(R.id.main_gallery);
        mGallery.setAdapter(mPanoAdapter);
        mGallery.setOnItemClickListener(mPanoAdapter);
        mGallery.setOnItemLongClickListener(mPanoAdapter);

        mShareButton = (Button) findViewById(R.id.main_button_share);
        mShareButton.setVisibility(View.INVISIBLE);
        mShareButton.setOnClickListener(this);

        mRestitchButton = (Button) findViewById(R.id.main_button_restitch);
        mRestitchButton.setVisibility(View.INVISIBLE);
        mRestitchButton.setOnClickListener(this);
    }

    /**
     * 
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshView();
    }

    /**
     * Initializes all the settings
     */
    private boolean loadSettings(String arg) {
        mSettings = getSharedPreferences(arg, Context.MODE_PRIVATE);
        mDirPath = mSettings.getString(SETTINGS_SAVE_PATH, mDefaultPath);
        mImagePrefix = mSettings.getString(SETTINGS_IMAGE_PREFIX, mDefaultImagePrefix);
        mOutputImage = mSettings.getString(SETTINGS_OUTPUT_IMAGE, mDefaultOutputName);
        mWarpType = mSettings.getString(SETTINGS_WARP_TYPE, mDefaultWarpType);
        mMatchConf = mSettings.getString(SETTINGS_MATCH_CONF, mDefaultMatchConf);
        mConfThresh = mSettings.getString(SETTINGS_CONF_THRESH, mDefaultConfThresh);
        mShowTip = mSettings.getBoolean(SETTINGS_SHOW_TIP, mDefaultShowTip);
        return true;
    }

    /**
     * Generates a new folder name, creates the folder, and returns the name
     */
    private void createNewPano() {
        createNewPano(true);
    }
    /**
     * Generates a new folder name, creates the folder, and returns the name
     */
    private void createNewPano(boolean capture) {
        // generate the default folder name
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        StringBuilder b = new StringBuilder(df.format(new Date()));

        mSubDir = b.toString()+ "/";
        mCurrentImage = 1;

        if (capture) {
            if (mShowTip) showDialog(DIALOG_FEATURE_COMPARISON);
            else capturePhoto();
        }
    }

    /**
     * Builds a new Intent to be passed to Camera Activity for taking a picture
     * @return Intent
     */
    private Intent createCaptureIntent() {
        if (mSubDir == null) createNewPano(false);
        Intent intent = new Intent(
                MediaStore.ACTION_IMAGE_CAPTURE,
                Uri.fromFile(new File(mDirPath + mSubDir + mImagePrefix + mCurrentImage + mType)),
                getApplicationContext(), PanoCamera.class);
        intent.putExtra(PanoCamera.EXTRA_DIR_PATH, mDirPath + mSubDir);
        intent.putExtra(PanoCamera.EXTRA_FILE_NAME,
                mImagePrefix + mCurrentImage + mType);
        return intent;
    }

    /**
     * Starts a new activity with generated Intent
     * Attempts to close the FEATURE_COMPARISON Tip Dialog.
     * Continue if it hasn't been shown since we only want to make sure it isn't open
     */
    private void capturePhoto() {
        try {
            dismissDialog(DIALOG_FEATURE_COMPARISON);
        } catch (IllegalArgumentException e) {
            // Catch and continue, make sure dialog is gone (see method doc)
        }
        startActivityForResult(createCaptureIntent(), PanoCamera.INTENT_TAKE_PICTURE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_advanced:
            // Show advanced editor
            startActivity(new Intent(this, AdvancedMenuActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Processes data from start activity for results
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PanoCamera.INTENT_TAKE_PICTURE) {
            if (resultCode == RESULT_OK) {
                showDialog(DIALOG_RESULTS);
            }
        }
    }

    /**
     * Creates dialog static data
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder;
        switch(id) {
        case DIALOG_FEATURE_COMPARISON:
            Dialog tip = new Dialog(this);
            tip.setContentView(R.layout.tip);
            tip.setTitle(R.string.dialog_tip);
            tip.setCancelable(true);

            dialog = tip;
            break;
        case DIALOG_RESULTS:
            Dialog progress = new Dialog(this);
            progress.setContentView(R.layout.result);
            progress.setTitle(R.string.dialog_results_title);
            progress.setCancelable(false);

            dialog = progress;
            break;
        case DIALOG_STITCHING:
            ProgressDialog stitching = ProgressDialog.show(PanoActivity.this, "",
                    getResources().getString(R.string.dialog_stitching), true);
            stitching.setCancelable(false);
            dialog = stitching;
            break;
        case DIALOG_ERROR:
            builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialog_error)
                   .setCancelable(false)
                   .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        //PanoActivity.this.finish();
                        dismissDialog(DIALOG_ERROR);
                    }
                });
            dialog = builder.create();
            break;
        case DIALOG_SUCCESS:
            Dialog success = new Dialog(this);
            success.setContentView(R.layout.success);
            success.setTitle(R.string.success);
            success.setCancelable(true);

            dialog = success;
            break;
        case DIALOG_CONTEXT_MENU:
            final String[] options = getResources().getStringArray(R.array.gallery_context_items);
            builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_gallery_context);
            builder.setItems(options, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int position) {
                    if (options[position].equals(getResources()
                            .getString(R.string.gallery_context_delete))) {
                        dismissDialog(DIALOG_CONTEXT_MENU);
                        deletePano();
                    }
                }
                
            });
            dialog = builder.create();
            break;
        default:
            dialog = null;
            break;
        }
        return dialog;
    }

    /**
     * Prepares dialogs dynamic content
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
        case DIALOG_RESULTS:
            refreshView();

            Mat mIntermediate = new Mat();
            Mat mYuv = new Mat();

            mIntermediate = Highgui.imread(mDirPath +mSubDir+
                    mImagePrefix + mCurrentImage + mType);

            Core.transpose(mIntermediate, mYuv);
            Core.flip(mYuv, mIntermediate, 1);

            Imgproc.resize(mIntermediate, mYuv, new Size(), 0.25, 0.25, Imgproc.CV_INTER_AREA);

            /** Currently Not Working in OpenCV **/
            /*
            Bitmap jpg = Bitmap.createBitmap(mIntermediate.cols(), mIntermediate.rows(),
                    Bitmap.Config.ARGB_8888);
            android.MatToBitmap(mIntermediate, jpg);
            */
            /** So we resort to this method **/
            Highgui.imwrite(mDirPath +mSubDir+ mImagePrefix +
                    mCurrentImage + smallType, mYuv);
            Bitmap jpg = BitmapFactory.decodeFile(mDirPath +mSubDir+
                    mImagePrefix + mCurrentImage + smallType);
            /** **/
            
            // cleanup
            mIntermediate.dispose();
            mYuv.dispose();

            ImageView image = (ImageView) dialog.findViewById(R.id.image);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setAdjustViewBounds(true);
            image.setPadding(2, 2, 2, 2);
            image.setImageBitmap(jpg);

            Button capture = (Button) dialog.findViewById(R.id.capture);
            capture.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    mCurrentImage++;
                    capturePhoto();
                }
            });
            Button retake = (Button) dialog.findViewById(R.id.retake);
            retake.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    capturePhoto();
                }
            });
            Button stitch = (Button) dialog.findViewById(R.id.stitch);
            stitch.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    new StitchPhotoTask().execute();
                }
            });

            break;
        case DIALOG_SUCCESS:
            final File img = new File(mDirPath + mSubDir + mOutputImage);
            Bitmap result = BitmapFactory.decodeFile(img.getAbsolutePath());

            ImageView png = (ImageView) dialog.findViewById(R.id.image);
            png.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            png.setAdjustViewBounds(true);
            png.setPadding(3, 3, 3, 3);
            png.setImageBitmap(result);

            refreshImage(mGalleryImage);
            refreshView();

            Button share = (Button) dialog.findViewById(R.id.share);
            share.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    shareImage(img);
                    dismissDialog(DIALOG_SUCCESS);
                }
            });

            Button exit = (Button) dialog.findViewById(R.id.close);
            exit.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    dismissDialog(DIALOG_SUCCESS);
                }
            });
            break;
        case DIALOG_FEATURE_COMPARISON:
            CheckBox box = (CheckBox) dialog.findViewById(R.id.show_tip);
            box.setChecked(mShowTip);
            box.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    mShowTip = ((CheckBox)v).isChecked();
                    SharedPreferences settings = getSharedPreferences(SETTINGS, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean(SETTINGS_SHOW_TIP, mShowTip);
                    editor.commit();
                }
            });

            Button cont = (Button) dialog.findViewById(R.id.tip_continue);
            cont.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    capturePhoto();
                }
            });
            break;
        default:
            super.onPrepareDialog(id, dialog);
        }
    }

    /**
     * Stitches together the set of images and presents the result to the user via a Dialog
     */
    class StitchPhotoTask extends AsyncTask<Void, Void, Integer> {

        /**
         * Shows a Progress Dialog to the user
         * Attempts to close the RESULTS dialog. We just catch the execption if it isn't open
         * and continue, since we really only care about ensuring that the dialog is closed.
         */
        @Override
        protected void onPreExecute() {
            try {
                dismissDialog(DIALOG_RESULTS);
            } catch (IllegalArgumentException e) {
                // catch and continue, we just want to make sure the dialog is gone
            }
            // Try to free up some memory before stitching
            System.gc();
            showDialog(DIALOG_STITCHING);
        }

        /**
         * Stitches the images
         * Passes data to native code via string array
         */
        @Override
        protected Integer doInBackground(Void... v) {
            List<String> s = new ArrayList<String>();
            s.add("Stitch");
            for (int i = 0; i < mCurrentImage; i++) {
                s.add(mDirPath + mSubDir + mImagePrefix + (i+1) + smallType);
            }
            s.add("--warp");
            s.add(mWarpType);
            s.add("--conf_thresh");
            s.add(mConfThresh);
            s.add("--match_conf");
            s.add(mMatchConf);
            s.add("--work_megapix");
            s.add("0.2");
            s.add("--seam_megapix");
            s.add("0.2");
            s.add("--expos_comp");
            s.add("gain");
            s.add("--output");
            s.add(mDirPath + mSubDir + mOutputImage);
            return Stitch(s.toArray());
        }

        /**
         * Builds response for user based on stitch status
         */
        @Override
        protected void onPostExecute(Integer ret) {
            dismissDialog(DIALOG_STITCHING);
            if (ret == 0) showDialog(DIALOG_SUCCESS);
            else showDialog(DIALOG_ERROR);
        }
    }

    /**
     * Natively stitches images together. Takes a string array of equivalent command line arguments
     * @param args
     * @return
     */
    public native int Stitch(Object[] args);

    /**
     * Loads Native Libraries
     */
    static {
        System.load("/data/data/net.pandorica.opencv.pano/lib/libprecomp.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libutil.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libmatchers.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libautocalib.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libblenders.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libexposure_compensate.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libmotion_estimators.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libseam_finders.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libwarpers.so");
        System.load("/data/data/net.pandorica.opencv.pano/lib/libopencv_stitcher.so");
    }

    /**
     * Displays Gallery View to User.
     * Updates ImageView on Click.
     */
    public class PanoAdapter extends BaseAdapter implements OnItemClickListener, OnItemLongClickListener {
        private Context mContext;
        private int itemBackground;

        PanoAdapter(Context c) {
            mContext = c;

            TypedArray typeArray = obtainStyledAttributes(R.styleable.main_gallery);
            itemBackground = typeArray.getResourceId(R.styleable.main_gallery_android_galleryItemBackground, 0);
            typeArray.recycle();
            updateFolders();
        }

        @Override
        public int getCount() {
            return mDirectories.size() + 1;
        }

        @Override
        public Object getItem(int arg0) {
            return arg0;
            }

        @Override
        public long getItemId(int arg0) {
            return arg0;
        }

        /**
         * Generates tumbnails for past panoramas
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView panos = new ImageView (mContext);
            Bitmap result = null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            // initialize our new pano image view
            if (position == 0) {
                result = BitmapFactory.decodeResource(getResources(), R.drawable.ic_add, options);
            }
            // otherwise initialize the sorted panorama's
            else {
                File f = getDirImage(position-1);
                if (f != null) {
                    result = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
                }
            }
            panos.setImageBitmap(result);
            panos.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            panos.setLayoutParams(new Gallery.LayoutParams(150, 150));
            panos.setBackgroundResource(itemBackground);
            return panos;
        }

        /**
         * Updates ImageView or starts a new pano
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            refreshImage(position);

            if (position == 0) createNewPano();
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view,
                int position, long id) {
            mGalleryLongClick = position;
            if (position > 0) showDialog(DIALOG_CONTEXT_MENU);
            return true;
        }
    }
    
    private void refreshImage(int position) {
        mGalleryImage = position;

        // image removed or new pano started, so clean up screen
        if (position == 0) {
            mGallery.setSelected(false);
            mShareButton.setVisibility(View.INVISIBLE);
            mRestitchButton.setVisibility(View.INVISIBLE);
            mImageSwitcher.setImageResource(R.drawable.ic_pixel);
        } else {
        // re-stitch / selection
            mGallery.setSelection(position);
            mShareButton.setVisibility(View.VISIBLE);
            mRestitchButton.setVisibility(View.VISIBLE);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            mCurrentImage = getDirCount(position - 1);
            File f = getDirImage(position - 1);
            if (f != null) {
                mSubDir = f.getParentFile().getName() + "/";
                mImageSwitcher.setImageDrawable(new BitmapDrawable(
                        BitmapFactory.decodeFile(getDirImage(position - 1)
                                .getAbsolutePath(), options)));
            }
        }
    }

    /**
     * Refreshes the Gallery View
     */
    private void refreshView() {
        updateFolders();
        mPanoAdapter.notifyDataSetChanged();
    }

    /**
     * Refreshes the list of current folders used to build the gallery view
     */
    private void updateFolders() {
        File storage = new File(mDirPath);
        if (!storage.exists()) storage.mkdirs();
        File[] contents = storage.listFiles();
        mDirectories = new ArrayList<File>();

        for (int i = 0; i < contents.length; i++) {
            if (contents[i].isDirectory()) {
                mDirectories.add(contents[i]);
            }
        }
    }

    private void deletePano() {
        File folder = mDirectories.get(mGalleryLongClick-1);
        deleteFolder(folder);
        updateFolders();
        int refresh = 0;
        if (mGalleryImage < mGalleryLongClick) refresh = mGalleryImage;
        if (mGalleryImage > mGalleryLongClick) refresh = (mGalleryImage -1);

        refreshImage(refresh);
        refreshView();
    }

    private void deleteFolder(File folder) {
        if (folder.exists()) {
            File[] contents = folder.listFiles();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i].isDirectory()) {
                    deleteFolder(contents[i]);
                } else {
                    contents[i].delete();
                }
            }
            folder.delete();
        }
    }

    /**
     * Determines the number of pictures taken for the folder at index
     * @param index
     * @return The number of images.
     */
    private int getDirCount(int index) {
        class FileNameF implements FilenameFilter {
            public boolean accept(File dir, String name) {
                return (name.startsWith(mImagePrefix) && name.endsWith(smallType));
            }
        }
        FilenameFilter filter = new FileNameF();
        File[] contents = mDirectories.get(index).listFiles(filter);
        return contents.length;
    }

    /**
     * Returns the file do display in the Gallery view or Image View to user for the given index
     * @param index
     * @return the File
     */
    private File getDirImage(int index) {
        File[] contents = mDirectories.get(index).listFiles();
        int hasResult = -1;
        int hasFirst = -1;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i].isFile()) {
                if (contents[i].getName().equals(mOutputImage)) hasResult = i;
                else if (contents[i].getName().equals(mImagePrefix + 1 + smallType)) hasFirst = i;
            }
        }
        if ((hasResult < 0) && (hasFirst < 0)) {
            return null;
        }

        if (hasResult >= 0) return contents[hasResult];
        else return contents[hasFirst];
    }

    /**
     * Creates the Image View that displays the panorama
     */
    @Override
    public View makeView() {
        ImageView imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFF000000);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setLayoutParams(new ImageSwitcher.LayoutParams(
                LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
        return imageView;
    }

    /**
     * Handles the different tasks for the different button clicks on the main screen
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.main_button_share:
            shareImage(getDirImage(mGalleryImage-1));
            break;
        case R.id.main_button_restitch:
            new StitchPhotoTask().execute();
            break;
        }
    }

    /**
     * Shares the file f through a generic intent
     * @param f
     */
    private void shareImage(File f) {
        Intent shareImage = new Intent(android.content.Intent.ACTION_SEND);
        shareImage.setType(MIME_TYPE);
        shareImage.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
        startActivity(Intent.createChooser(shareImage,
                getResources().getString(R.string.intent_share_using)));
    }
}
