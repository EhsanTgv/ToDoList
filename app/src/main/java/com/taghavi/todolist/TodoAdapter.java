package com.taghavi.todolist;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.aakira.expandablelayout.ExpandableLinearLayout;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public class TodoAdapter extends ArrayAdapter<String> {
    private Context context;
    private int resource;
    private List<String> objects;
    String LOG_TAG = TodoAdapter.class.getSimpleName();

    onImageClickedListener imageClickedListener;

    public TodoAdapter(@Nonnull Context context, int resource, @Nonnull List<String> objects) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.objects = objects;

        imageClickedListener = (onImageClickedListener) context;
    }

    public interface onImageClickedListener {
        void OnImageClicked(int position);
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(resource, parent, false);
        }

        View rootLayout = convertView.findViewById(R.id.root_layout);
        final ExpandableLinearLayout expandView = convertView.findViewById(R.id.expand_view);
        final ImageView btnImage = convertView.findViewById(R.id.btn_image);
        final ImageView imgExpanded = convertView.findViewById(R.id.img_expanded);

        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expandView.toggle();
            }
        });

        btnImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageClickedListener.OnImageClicked(position);
            }
        });

        // Apply the data to the to-do list
        setItemTextFromFirestore(convertView, position);
        downloadImage(position, imgExpanded);

        return convertView;
    }

    private void downloadImage(int position, final ImageView imgExpanded) {
        String itemName = objects.get(position);
        final File imageFile = new File(context.getCacheDir(), itemName);

        FirebaseStorage storage = FirebaseStorage.getInstance();
        String userId = FirebaseAuth.getInstance().getUid();
        StorageReference imageRef = storage.getReference().child(userId).child("image").child(itemName);

        imageRef.getFile(Uri.fromFile(imageFile))
                .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        Log.d(LOG_TAG, "Image download successful: " + imageFile.getPath());
                        imgExpanded.setImageURI(Uri.fromFile(imageFile));
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(LOG_TAG, "Image download failed: " + e.getMessage());
                    }
                });
    }

    private void setItemTextFromFirestore(View convertView, final int position) {
        ((TextView) convertView.findViewById(R.id.tv_item)).setText(this.objects.get(position));
        convertView.findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                objects.remove(position);
                notifyDataSetChanged();

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                String uid = FirebaseAuth.getInstance().getUid();

                Map<String, Object> userMap = new HashMap<>();
                userMap.put("todoList", objects);
                db.collection("users").document(uid).set(userMap);

                setDeletedItemProperty();
            }
        });
    }

    private void setDeletedItemProperty() {
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(getContext());
        analytics.setUserProperty("item_deleted", "true");
    }
}
