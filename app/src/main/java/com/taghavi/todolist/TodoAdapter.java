package com.taghavi.todolist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public class TodoAdapter extends ArrayAdapter<String> {
    private Context context;
    private int resource;
    private List<String> objects;

    public TodoAdapter(@Nonnull Context context, int resource, @Nonnull List<String> objects) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.objects = objects;
    }

    @NonNull
    @Override
    public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(resource, parent, false);
        }
        ((TextView) convertView.findViewById(R.id.itemTextView)).setText(this.objects.get(position));

        convertView.findViewById(R.id.itemDeleteButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                objects.remove(position);
                notifyDataSetChanged();

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                String userId = FirebaseAuth.getInstance().getUid();

                if (userId!=null) {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("todoList", objects);
                    db.collection("users").document(userId).set(userMap);
                }else {
                    Toast.makeText(context, "There is something wrong", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return convertView;
    }
}
