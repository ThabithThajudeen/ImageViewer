package com.example.myapplication;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;


public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;
    private boolean isDoubleColumn = false;




    private ImageView imageView;
    private ProgressBar progressBar;
    private Button searchButton;

    private Button toggleViewButton;
    private static final String PIXABAY_API_KEY = "39753460-0fb046c7ef660ea6ea48973f7";
    private static final String PIXABAY_API_URL = "https://pixabay.com/api/?q=flowers&key=" + PIXABAY_API_KEY;


    private static final int PICK_IMAGE_REQUEST = 1;
    private Button selectImageButton;
    private Button uploadButton;
    private Uri imageUri;
    private StorageReference storageReference;
    private static final int PERMISSIONS_REQUEST_WRITE_STORAGE = 1234;

    private String selectedImageUrl;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);

        recyclerView = findViewById(R.id.recyclerView);
        imageAdapter = new ImageAdapter();
        recyclerView.setAdapter(imageAdapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, isDoubleColumn ? 2 : 1));

        storageReference = FirebaseStorage.getInstance().getReference();

        toggleViewButton = findViewById(R.id.toggleViewButton);
        toggleViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isDoubleColumn) {
                    recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, 1));
                } else {
                    recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, 2));
                }
                isDoubleColumn = !isDoubleColumn;
                imageAdapter.notifyDataSetChanged();
            }
        });

        progressBar = findViewById(R.id.progressBar);
        searchButton = findViewById(R.id.searchButton);
        EditText searchEditText = findViewById(R.id.searchEditText);

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = searchEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    String apiUrl = "https://pixabay.com/api/?q=" + query + "&key=" + PIXABAY_API_KEY;
                    new ImageSearchTask().execute(apiUrl);
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a search query.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        storageReference = FirebaseStorage.getInstance().getReference("uploads");


        Button uploadButton = findViewById(R.id.uploadButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImageToFirebase();
            }
        });

        imageView = findViewById(R.id.imageView);

        imageAdapter.setOnImageClickListener(new ImageAdapter.OnImageClickListener() {
            @Override
            public void onImageClicked(String imageUrl) {
                selectedImageUrl = imageUrl;
                Picasso.get().load(selectedImageUrl).into(imageView);
            }
        });
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            ImageView imageView = findViewById(R.id.imageView);
            imageView.setImageURI(imageUri);
        }
    }

    private void uploadImageToFirebase() {
        if (selectedImageUrl != null) {
            Glide.with(MainActivity.this)
                    .asBitmap()
                    .load(selectedImageUrl)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            // Save the bitmap to a file
                            File file = new File(getCacheDir(), "tempFile.png");
                            try {
                                FileOutputStream outStream = new FileOutputStream(file);
                                resource.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                                outStream.close();

                                // Upload the saved file to Firebase
                                StorageReference fileRef = storageReference.child("images/" + UUID.randomUUID().toString());
                                fileRef.putFile(Uri.fromFile(file)).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        Toast.makeText(MainActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e("FirebaseUpload", "Upload failed", e);
                                        Toast.makeText(MainActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            } catch (IOException e) {
                                e.printStackTrace();
                                Toast.makeText(MainActivity.this, "Error processing image.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } else {
            Toast.makeText(MainActivity.this, "Please select an image first.", Toast.LENGTH_SHORT).show();
        }
    }



    private class ImageSearchTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(String... urls) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(urls[0]).build();

            try {
                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.INVISIBLE);

            if (result != null) {
                try {
                    JSONObject json = new JSONObject(result);
                    JSONArray hits = json.getJSONArray("hits");

                    List<String> imageUrls = new ArrayList<>();

                    for (int i = 0; i < hits.length(); i++) {
                        String imageUrl = hits.getJSONObject(i).getString("webformatURL");
                        imageUrls.add(imageUrl);
                    }

                    imageAdapter.setImageUrls(imageUrls);
                    recyclerView.setVisibility(View.VISIBLE);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Error parsing data.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Error fetching data.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}