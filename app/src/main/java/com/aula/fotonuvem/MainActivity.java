package com.aula.fotonuvem;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.cloudinary.android.preprocess.BitmapDecoder;
import com.cloudinary.android.preprocess.BitmapEncoder;
import com.cloudinary.android.preprocess.DimensionsValidator;
import com.cloudinary.android.preprocess.ImagePreprocessChain;
import com.cloudinary.android.preprocess.Limit;
import com.cloudinary.android.preprocess.Rotate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ImageView imgFoto;
    private Button btnTirarFoto, btnGaleria, btnEnviar;
    private ProgressBar progress;
    private TextView txtStatus;

    private Uri fotoUri;

    private String urlNuvem;

    private ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            tirouFoto -> {
                if(tirouFoto != null && tirouFoto){
                   imgFoto.postDelayed(() -> {
                       imgFoto.setImageURI(null);
                       imgFoto.setImageURI(fotoUri);
                       txtStatus.setText("Foto tirada com sucesso!");
                       btnEnviar.setEnabled(true);

                   }, 2000);
                }
            });

    //configura galeria (Android)
    private ActivityResultLauncher<Intent> galeriaLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == RESULT_OK){
                        if(result.getData() != null){
                            fotoUri = result.getData().getData();
                            imgFoto.postDelayed(() -> {
                                imgFoto.setImageURI(null);
                                imgFoto.setImageURI(fotoUri);
                                txtStatus.setText("Carregada com sucesso!");
                                btnEnviar.setEnabled(true);
                            }, 2000);
                        }
                    }
                }
            }
    );



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initCloudinary();

        imgFoto = findViewById(R.id.imgFoto);
        btnTirarFoto = findViewById(R.id.btnTirarFoto);
        btnEnviar = findViewById(R.id.btnEnviar);
        btnGaleria = findViewById(R.id.btnGaleria);
        progress = findViewById(R.id.progress);
        txtStatus = findViewById(R.id.txtStatus);

        //recuperar dados da tela ao girar
        if(savedInstanceState != null){
            fotoUri = Uri.parse(savedInstanceState.getString("fotoUri"));
            imgFoto.setImageURI(fotoUri);
            txtStatus.setText(savedInstanceState.getString("txtStatus"));

        }

        btnTirarFoto.setOnClickListener(v -> {
            tirarFoto();
        });

        btnGaleria.setOnClickListener(v -> {
            abrirGaleria();
        });

        btnEnviar.setOnClickListener( v -> {
            salvarGaleria();

            formatar_enviarNuvem();
        });

        txtStatus.setOnClickListener(v -> {
            Intent rota = new Intent(this, VerImagem.class);
            rota.putExtra("url", urlNuvem);
            startActivity(rota);
        });
    }

    private void salvarGaleria(){
        //Configurar foto para galeria
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "TurmaB" + System.currentTimeMillis() / 1000 + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TurmaB");

        Uri imagemSalvar = getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            InputStream inputStream = getContentResolver().openInputStream(fotoUri);
            OutputStream outputStream = getContentResolver().openOutputStream(imagemSalvar);

            byte[] buffer = new byte[1024];
            int bytesLidos;
            while ((bytesLidos = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesLidos);
            }
        } catch (Exception e) {
            e.printStackTrace();
            }


    }

    private void formatar_enviarNuvem() {
        if (fotoUri == null) return;

        progress.setVisibility(View.VISIBLE);
        btnEnviar.setEnabled(false);
        txtStatus.setText("Enviando...");

        // Realmente enviar para o cloudinary
        MediaManager.get()
                .upload(fotoUri)
                .option("folder","turmaB")
                .unsigned("fotonuvem")
                .preprocess(new ImagePreprocessChain()
                        .loadWith(new BitmapDecoder(1000, 1000))
                        .addStep(new Limit(1000, 1000))
                        .addStep(new DimensionsValidator(10,10,1000,1000))
                        .addStep(new AutoRotation(getApplicationContext(), fotoUri))
                        .saveWith(new BitmapEncoder(BitmapEncoder.Format.WEBP, 80)))
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {

                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // opcional
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // recebe a URL da imagem hospedada
                        urlNuvem = (String) resultData.get("secure_url");
                        progress.setVisibility(View.GONE);
                        btnEnviar.setEnabled(true);
                        txtStatus.setText(urlNuvem);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e("Cloudinary", error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {

                    }
                }).dispatch(this);
    }
    private void initCloudinary() {
        try {
            Map config = new HashMap();
            config.put("cloud_name", "z8iyapuj");
            MediaManager.init(this, config);
        } catch (Exception e) {
            Log.i("Cloudinary", e.getMessage());
        }
    }

    private void abrirGaleria() {
        Intent galeria = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galeriaLauncher.launch(galeria);
    }

    private void tirarFoto() {
        // habilitar as configurações para SALVAR a foto no celular

        File arquivo = new File(getExternalFilesDir(null), "minhafoto.jpg");

        fotoUri = FileProvider.getUriForFile
                (this, getPackageName() + ".fileprovider", arquivo);

        // ver no log o caminho
        System.out.println(fotoUri);

        // abrir a câmera (android)
        cameraLauncher.launch(fotoUri);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (fotoUri != null) {
            outState.putString("fotoUri", fotoUri.toString());
            outState.putString("status", txtStatus.getText().toString());
        }
    }
}