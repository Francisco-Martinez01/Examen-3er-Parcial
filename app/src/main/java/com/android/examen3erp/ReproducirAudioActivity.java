package com.android.examen3erp;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

public class ReproducirAudioActivity extends AppCompatActivity {

    public static final String EXTRA_AUDIO_URL = "AUDIO_URL";
    public static final String EXTRA_DESCRIPCION = "DESCRIPCION";

    private TextView tvTituloAudio, tvDescripcion, tvEstado;
    private Button btnPlayPause, btnStop;
    private SeekBar seekBar;

    private MediaPlayer mediaPlayer;
    private String audioUrl;

    private final Handler handler = new Handler();
    private boolean isPrepared = false;
    private boolean userSeeking = false;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPrepared && !userSeeking) {
                int pos = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(pos);
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reproducir_audio);

        tvTituloAudio = findViewById(R.id.tvTituloAudio);
        tvDescripcion = findViewById(R.id.tvDescripcion);
        tvEstado = findViewById(R.id.tvEstado);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnStop = findViewById(R.id.btnStop);
        seekBar = findViewById(R.id.seekBar);

        audioUrl = getIntent().getStringExtra(EXTRA_AUDIO_URL);
        String descripcion = getIntent().getStringExtra(EXTRA_DESCRIPCION);

        if (descripcion != null) tvDescripcion.setText(descripcion);

        if (audioUrl == null || audioUrl.isEmpty()) {
            Toast.makeText(this, "URL de audio no válida", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        prepararMediaPlayer();

        btnPlayPause.setOnClickListener(v -> {
            if (!isPrepared) return;
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setText("Play");
            } else {
                mediaPlayer.start();
                btnPlayPause.setText("Pause");
            }
        });

        btnStop.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
                btnPlayPause.setText("Play");
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (mediaPlayer != null && isPrepared) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void prepararMediaPlayer() {
        tvEstado.setText("Cargando...");
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                tvEstado.setText("Listo");
                seekBar.setMax(mp.getDuration());
                btnPlayPause.setEnabled(true);
                btnStop.setEnabled(true);
                handler.post(progressRunnable);
            });
            mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayPause.setText("Play");
                seekBar.setProgress(0);
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                tvEstado.setText("Error de reproducción");
                Toast.makeText(this, "Error reproduciendo audio", Toast.LENGTH_LONG).show();
                return true;
            });
            btnPlayPause.setEnabled(false);
            btnStop.setEnabled(false);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            tvEstado.setText("Error al cargar");
            Toast.makeText(this, "No se pudo preparar el audio", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setText("Play");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}