package com.android.examen3erp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditarEntrevistaActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "ENTREVISTA_ID";

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth auth;

    private EditText etDescripcion, etPeriodista, etFecha;
    private TextInputLayout tilFecha;
    private ImageView ivImagenPreview;
    private TextView tvAudioSeleccionado;
    private Button btnReemplazarImagen, btnReemplazarAudio, btnGuardarCambios, btnEliminar;

    private String entrevistaId;
    private String imagenUrlActual;
    private String audioUrlActual;

    private byte[] imagenBytesNueva = null;
    private Uri imagenUriNueva = null;
    private Uri audioUriNuevo = null;

    private final SimpleDateFormat fechaFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private MaterialDatePicker<Long> datePicker;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Void> takePicturePreview =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos);
                    imagenBytesNueva = baos.toByteArray();
                    imagenUriNueva = null;
                    ivImagenPreview.setImageBitmap(bitmap);
                } else {
                    Toast.makeText(this, "No se capturó la foto", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) takePicturePreview.launch(null);
                else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            });

    // Audio
    private final ActivityResultLauncher<Intent> recordAudio =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        audioUriNuevo = uri;
                        tvAudioSeleccionado.setText("Audio nuevo listo");
                    }
                } else {
                    Toast.makeText(this, "Grabación cancelada", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> requestAudioPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    Intent intent = new Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                    recordAudio.launch(intent);
                } else {
                    Toast.makeText(this, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_entrevista);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();

        entrevistaId = getIntent().getStringExtra(EXTRA_ID);
        if (entrevistaId == null || entrevistaId.isEmpty()) {
            Toast.makeText(this, "ID de entrevista inválido", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        etDescripcion = findViewById(R.id.etDescripcion);
        etPeriodista = findViewById(R.id.etPeriodista);
        etFecha = findViewById(R.id.etFecha);
        tilFecha = findViewById(R.id.tilFecha);
        ivImagenPreview = findViewById(R.id.ivImagenPreview);
        tvAudioSeleccionado = findViewById(R.id.tvAudioSeleccionado);
        btnReemplazarImagen = findViewById(R.id.btnReemplazarImagen);
        btnReemplazarAudio = findViewById(R.id.btnReemplazarAudio);
        btnGuardarCambios = findViewById(R.id.btnGuardarCambios);
        btnEliminar = findViewById(R.id.btnEliminar);

        datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona la fecha")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                etFecha.setText(fechaFormat.format(new Date(selection)));
            }
        });
        etFecha.setOnClickListener(v -> datePicker.show(getSupportFragmentManager(), "mdp"));
        tilFecha.setEndIconOnClickListener(v -> datePicker.show(getSupportFragmentManager(), "mdp"));

        cargarEntrevista();

        btnReemplazarImagen.setOnClickListener(v ->
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        );
        btnReemplazarAudio.setOnClickListener(v ->
                requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        );

        btnGuardarCambios.setOnClickListener(v -> guardarCambios());
        btnEliminar.setOnClickListener(v -> eliminarEntrevista());
    }

    private void cargarEntrevista() {
        db.collection("entrevistas").document(entrevistaId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Entrevista no encontrada", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    poblarUI(doc);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void poblarUI(DocumentSnapshot doc) {
        String descripcion = doc.getString("Descripcion");
        String periodista = doc.getString("Periodista");
        Timestamp ts = doc.getTimestamp("Fecha");
        imagenUrlActual = doc.getString("imagenUrl");
        audioUrlActual = doc.getString("audioUrl");

        etDescripcion.setText(descripcion != null ? descripcion : "");
        etPeriodista.setText(periodista != null ? periodista : "");
        if (ts != null) etFecha.setText(fechaFormat.format(ts.toDate()));

        if (imagenUrlActual != null && !imagenUrlActual.isEmpty()) {
            Glide.with(this).load(imagenUrlActual).centerCrop().into(ivImagenPreview);
        } else {
            ivImagenPreview.setImageDrawable(null);
        }

        tvAudioSeleccionado.setText(
                audioUrlActual != null && !audioUrlActual.isEmpty() ? "Audio existente" : "Sin audio"
        );
    }

    private void guardarCambios() {
        String descripcion = etDescripcion.getText().toString().trim();
        String periodista = etPeriodista.getText().toString().trim();
        String fechaStr = etFecha.getText().toString().trim();

        if (descripcion.isEmpty() || periodista.isEmpty() || fechaStr.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }
        Timestamp timestamp = parseFechaToTimestamp(fechaStr);
        if (timestamp == null) {
            Toast.makeText(this, "Fecha inválida. Usa formato YYYY-MM-DD.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardarCambios.setEnabled(false);

        executor.execute(() -> {
            try {
                String basePath = "entrevistas/" + entrevistaId;
                String imagenUrl = imagenUrlActual;
                String audioUrl = audioUrlActual;

                // Reemplazo de imagen si hay nuevo contenido
                if (imagenBytesNueva != null && imagenBytesNueva.length > 0) {
                    StorageReference ref = storage.getReference().child(basePath + "/imagen.jpg");
                    Uri uri = Tasks.await(
                            ref.putBytes(imagenBytesNueva)
                                    .continueWithTask(task -> {
                                        if (!task.isSuccessful()) throw task.getException();
                                        return ref.getDownloadUrl();
                                    })
                    );
                    imagenUrl = uri.toString();
                } else if (imagenUriNueva != null) {
                    String ext = guessExtensionFromUri(imagenUriNueva, "jpg");
                    StorageReference ref = storage.getReference().child(basePath + "/imagen." + ext);
                    Uri uri = Tasks.await(
                            ref.putFile(imagenUriNueva)
                                    .continueWithTask(task -> {
                                        if (!task.isSuccessful()) throw task.getException();
                                        return ref.getDownloadUrl();
                                    })
                    );
                    imagenUrl = uri.toString();
                }

                if (audioUriNuevo != null) {
                    String ext = guessExtensionFromUri(audioUriNuevo, "mp3");
                    StorageReference ref = storage.getReference().child(basePath + "/audio." + ext);
                    Uri uri = Tasks.await(
                            ref.putFile(audioUriNuevo)
                                    .continueWithTask(task -> {
                                        if (!task.isSuccessful()) throw task.getException();
                                        return ref.getDownloadUrl();
                                    })
                    );
                    audioUrl = uri.toString();
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("Descripcion", descripcion);
                updates.put("Periodista", periodista);
                updates.put("Fecha", timestamp);
                updates.put("imagenUrl", imagenUrl);
                updates.put("audioUrl", audioUrl);

                Tasks.await(db.collection("entrevistas").document(entrevistaId).update(updates));

                runOnUiThread(() -> {
                    Toast.makeText(this, "Cambios guardados", Toast.LENGTH_LONG).show();
                    btnGuardarCambios.setEnabled(true);
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                Log.e("EditarEntrevista", "Error al guardar", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnGuardarCambios.setEnabled(true);
                });
            }
        });
    }

    private void eliminarEntrevista() {
        btnEliminar.setEnabled(false);

        executor.execute(() -> {
            try {
                if (imagenUrlActual != null && !imagenUrlActual.isEmpty()) {
                    try {
                        Tasks.await(FirebaseStorage.getInstance().getReferenceFromUrl(imagenUrlActual).delete());
                    } catch (Exception ignore) {}
                }
                if (audioUrlActual != null && !audioUrlActual.isEmpty()) {
                    try {
                        Tasks.await(FirebaseStorage.getInstance().getReferenceFromUrl(audioUrlActual).delete());
                    } catch (Exception ignore) {}
                }

                Tasks.await(db.collection("entrevistas").document(entrevistaId).delete());

                runOnUiThread(() -> {
                    Toast.makeText(this, "Entrevista eliminada", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                Log.e("EditarEntrevista", "Error al eliminar", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnEliminar.setEnabled(true);
                });
            }
        });
    }

    private Timestamp parseFechaToTimestamp(String fechaStr) {
        try {
            Date date = fechaFormat.parse(fechaStr);
            return date != null ? new Timestamp(date) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String guessExtensionFromUri(Uri uri, String defaultExt) {
        String type = getContentResolver().getType(uri);
        if (type == null) return defaultExt;

        if (type.contains("jpeg")) return "jpg";
        if (type.contains("png")) return "png";
        if (type.contains("webp")) return "webp";
        if (type.contains("mp3")) return "mp3";
        if (type.contains("mpeg")) return "mp3";
        if (type.contains("wav")) return "wav";
        if (type.contains("aac")) return "aac";
        return defaultExt;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!executor.isShutdown()) executor.shutdown();
    }
}