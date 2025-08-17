package com.android.examen3erp;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.Timestamp;

import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IngresoEntrevistaActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth auth;

    private EditText etDescripcion;
    private EditText etPeriodista;
    private EditText etFecha;
    private TextInputLayout tilFecha;
    private ImageView ivImagenPreview;
    private TextView tvAudioSeleccionado;
    private Button btnSeleccionarImagen;
    private Button btnSeleccionarAudio;
    private Button btnGuardar;

    private Uri imagenUri = null;
    private Uri audioUri = null;
    private byte[] imagenBytes = null;
    private boolean isSignedIn = false;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Toma foto con preview (bitmap)
    private final ActivityResultLauncher<Void> takePicturePreview =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos);
                    imagenBytes = baos.toByteArray();
                    imagenUri = null;
                    ivImagenPreview.setImageBitmap(bitmap);
                } else {
                    Toast.makeText(this, "No se capturó la foto", Toast.LENGTH_SHORT).show();
                }
            });

    // Permiso cámara
    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    takePicturePreview.launch(null);
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                }
            });

    // Recibe audio grabado
    private final ActivityResultLauncher<android.content.Intent> recordAudio =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        audioUri = uri;
                        tvAudioSeleccionado.setText("Audio grabado");
                    } else {
                        Toast.makeText(this, "No se obtuvo audio", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Grabación cancelada", Toast.LENGTH_SHORT).show();
                }
            });

    // Permiso micrófono
    private final ActivityResultLauncher<String> requestAudioPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    android.content.Intent intent =
                            new android.content.Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                    recordAudio.launch(intent);
                } else {
                    Toast.makeText(this, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show();
                }
            });

    private MaterialDatePicker<Long> datePicker;
    private final SimpleDateFormat fechaFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ingreso_entrevista);

        // Firebase
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();

        // Vistas
        etDescripcion = findViewById(R.id.etDescripcion);
        etPeriodista = findViewById(R.id.etPeriodista);
        etFecha = findViewById(R.id.etFecha);
        tilFecha = findViewById(R.id.tilFecha);
        ivImagenPreview = findViewById(R.id.ivImagenPreview);
        tvAudioSeleccionado = findViewById(R.id.tvAudioSeleccionado);
        btnSeleccionarImagen = findViewById(R.id.btnSeleccionarImagen);
        btnSeleccionarAudio = findViewById(R.id.btnSeleccionarAudio);
        btnGuardar = findViewById(R.id.btnGuardar);

        // Botones deshabilitados hasta autenticar
        btnSeleccionarImagen.setEnabled(false);
        btnSeleccionarAudio.setEnabled(false);
        btnGuardar.setEnabled(false);

        // Auth anónima
        auth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        isSignedIn = true;
                        btnSeleccionarImagen.setEnabled(true);
                        btnSeleccionarAudio.setEnabled(true);
                        btnGuardar.setEnabled(true);
                    } else {
                        Toast.makeText(this, "Auth falló: " + (task.getException()!=null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                    }
                });

        // DatePicker Material
        datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona la fecha")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                String fecha = fechaFormat.format(new Date(selection));
                etFecha.setText(fecha);
            }
        });

        // Abrir date picker al tocar el campo o el icono
        etFecha.setOnClickListener(v -> datePicker.show(getSupportFragmentManager(), "mdp"));
        tilFecha.setEndIconOnClickListener(v -> datePicker.show(getSupportFragmentManager(), "mdp"));

        // Listeners
        btnSeleccionarImagen.setOnClickListener(v ->
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        );

        btnSeleccionarAudio.setOnClickListener(v ->
                requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        );

        btnGuardar.setOnClickListener(v -> guardarEntrevista());
    }

    private void guardarEntrevista() {
        if (!isSignedIn || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Espera a que la app se autentique antes de guardar.", Toast.LENGTH_SHORT).show();
            return;
        }

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

        btnGuardar.setEnabled(false);

        executor.execute(() -> {
            try {
                // ID autogenerado para la entrevista
                String entrevistaId = db.collection("entrevistas").document().getId();
                String basePath = "entrevistas/" + entrevistaId;
                String imagenUrl = null;
                String audioUrl = null;

                // Subir imagen si hay
                if (imagenBytes != null && imagenBytes.length > 0) {
                    StorageReference ref = storage.getReference().child(basePath + "/imagen.jpg");
                    Uri uri = Tasks.await(
                            ref.putBytes(imagenBytes)
                                    .continueWithTask(task -> {
                                        if (!task.isSuccessful()) throw task.getException();
                                        return ref.getDownloadUrl();
                                    })
                    );
                    imagenUrl = uri.toString();
                } else if (imagenUri != null) { // por si algún día usas URI
                    String ext = guessExtensionFromUri(imagenUri, "jpg");
                    StorageReference ref = storage.getReference().child(basePath + "/imagen." + ext);
                    Uri uri = Tasks.await(
                            ref.putFile(imagenUri)
                                    .continueWithTask(task -> {
                                        if (!task.isSuccessful()) throw task.getException();
                                        return ref.getDownloadUrl();
                                    })
                    );
                    imagenUrl = uri.toString();
                }

                // Subir audio si hay
                if (audioUri != null) {
                    String ext = guessExtensionFromUri(audioUri, "mp3");
                    StorageReference ref = storage.getReference().child(basePath + "/audio." + ext);
                    Uri uri = Tasks.await(
                            ref.putFile(audioUri)
                                    .continueWithTask(task -> {
                                        if (!task.isSuccessful()) throw task.getException();
                                        return ref.getDownloadUrl();
                                    })
                    );
                    audioUrl = uri.toString();
                }

                // Guardar en Firestore
                Map<String, Object> doc = new HashMap<>();
                doc.put("IdEntrevista", entrevistaId);
                doc.put("Descripcion", descripcion);
                doc.put("Periodista", periodista);
                doc.put("Fecha", timestamp);
                doc.put("imagenUrl", imagenUrl);
                doc.put("audioUrl", audioUrl);

                // Usamos el ID autogenerado como ID del documento
                Tasks.await(db.collection("entrevistas").document(entrevistaId).set(doc));

                runOnUiThread(() -> {
                    Toast.makeText(IngresoEntrevistaActivity.this, "Entrevista guardada", Toast.LENGTH_LONG).show();
                    btnGuardar.setEnabled(true);
                    limpiarFormulario();
                });

            } catch (Exception e) {
                Log.e("GuardarEntrevista", "Error", e);
                runOnUiThread(() -> {
                    Toast.makeText(IngresoEntrevistaActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnGuardar.setEnabled(true);
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

    private void limpiarFormulario() {
        etDescripcion.getText().clear();
        etPeriodista.getText().clear();
        etFecha.getText().clear();
        tvAudioSeleccionado.setText("Sin audio seleccionado");
        ivImagenPreview.setImageDrawable(null);
        imagenUri = null;
        audioUri = null;
        imagenBytes = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}