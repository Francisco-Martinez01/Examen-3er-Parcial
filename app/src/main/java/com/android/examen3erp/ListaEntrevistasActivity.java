package com.android.examen3erp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//import javax.annotation.Nullable;

public class ListaEntrevistasActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private RecyclerView rv;
    private EntrevistaAdapter adapter;
    private ListenerRegistration registration;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_entrevistas);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rv = findViewById(R.id.rvEntrevistas);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        adapter = new EntrevistaAdapter();
        rv.setAdapter(adapter);

        Query q = db.collection("entrevistas").orderBy("Fecha", Query.Direction.DESCENDING);

        registration = q.addSnapshotListener((value, error) -> {
            if (error != null) {
                Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (value == null) return;

            List<EntrevistaItem> items = new ArrayList<>();
            for (DocumentSnapshot doc : value.getDocuments()) {
                String id = doc.getId();
                String descripcion = doc.getString("Descripcion");
                String periodista = doc.getString("Periodista");
                Timestamp ts = doc.getTimestamp("Fecha");
                String fecha = ts != null ? sdf.format(ts.toDate()) : "";
                String imagenUrl = doc.getString("imagenUrl");
                String audioUrl = doc.getString("audioUrl");
                items.add(new EntrevistaItem(id, descripcion, periodista, fecha, imagenUrl, audioUrl));
            }
            adapter.setItems(items);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) registration.remove();
    }

    static class EntrevistaItem {
        final String id;
        final String descripcion;
        final String periodista;
        final String fecha;
        final String imagenUrl;
        final String audioUrl;

        EntrevistaItem(String id, String descripcion, String periodista, String fecha, String imagenUrl, String audioUrl) {
            this.id = id;
            this.descripcion = descripcion;
            this.periodista = periodista;
            this.fecha = fecha;
            this.imagenUrl = imagenUrl;
            this.audioUrl = audioUrl;
        }
    }

    // Adapter RecyclerView
    class EntrevistaAdapter extends RecyclerView.Adapter<EntrevistaVH> {
        private List<EntrevistaItem> data = new ArrayList<>();

        void setItems(List<EntrevistaItem> items) {
            this.data = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EntrevistaVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entrevista, parent, false);
            return new EntrevistaVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull EntrevistaVH h, int position) {
            EntrevistaItem it = data.get(position);
            h.tvDescripcion.setText(it.descripcion != null ? it.descripcion : "(Sin descripción)");
            h.tvPeriodista.setText(it.periodista != null ? it.periodista : "(Sin periodista)");
            h.tvFecha.setText(it.fecha != null ? it.fecha : "");

            if (it.imagenUrl != null && !it.imagenUrl.isEmpty()) {
                Glide.with(h.ivThumb.getContext())
                        .load(it.imagenUrl)
                        .centerCrop()
                        .into(h.ivThumb);
            } else {
                h.ivThumb.setImageDrawable(null);
                h.ivThumb.setBackgroundColor(0xFFDDDDDD);
            }

            h.itemView.setOnClickListener(v -> {
                if (it.audioUrl != null && !it.audioUrl.isEmpty()) {
                    Intent intent = new Intent(v.getContext(), ReproducirAudioActivity.class);
                    intent.putExtra(ReproducirAudioActivity.EXTRA_AUDIO_URL, it.audioUrl);
                    intent.putExtra(ReproducirAudioActivity.EXTRA_DESCRIPCION, it.descripcion != null ? it.descripcion : "");
                    v.getContext().startActivity(intent);
                } else {
                    Toast.makeText(ListaEntrevistasActivity.this, "Sin audio", Toast.LENGTH_SHORT).show();
                }
            });
            h.itemView.setOnLongClickListener(v -> {
                Intent i = new Intent(v.getContext(), EditarEntrevistaActivity.class);
                i.putExtra("ENTREVISTA_ID", it.id);
                v.getContext().startActivity(i);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class EntrevistaVH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvDescripcion;
        TextView tvPeriodista;
        TextView tvFecha;

        EntrevistaVH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivThumb);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcion);
            tvPeriodista = itemView.findViewById(R.id.tvPeriodista);
            tvFecha = itemView.findViewById(R.id.tvFecha);
        }
    }
}