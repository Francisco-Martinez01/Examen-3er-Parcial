package com.android.examen3erp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MenuPrincipalActivity extends AppCompatActivity {

    private Button btnIrIngreso;
    private Button btnIrLista;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        btnIrIngreso = findViewById(R.id.btnIrIngreso);
        btnIrLista = findViewById(R.id.btnIrLista);

        btnIrIngreso.setOnClickListener(v ->
                startActivity(new Intent(this, IngresoEntrevistaActivity.class)));

        btnIrLista.setOnClickListener(v ->
                startActivity(new Intent(this, ListaEntrevistasActivity.class)));
    }
}