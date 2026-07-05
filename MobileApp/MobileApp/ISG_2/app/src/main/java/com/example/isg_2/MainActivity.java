package com.example.isg_2; // Kendi paket isminiz kalsın

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private CheckBox cbMask, cbHelmet, cbVest, cbGlasses, cbBelt;
    private CheckBox cbSuit, cbGloves, cbToolbox, cbWelding, cbEar;

    // IP AYARI:
    // Emülatör için: "10.0.2.2"
    // Gerçek telefon için: bilgisayarınızın yerel ağ IP'si (YOUR_PC_LAN_IP)
    private final String SERVER_IP = "10.0.2.2";
    private final int SERVER_PORT = 9999;

    private String currentRules = "{}";
    private boolean isRunning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        cbMask = findViewById(R.id.cb_mask);
        cbHelmet = findViewById(R.id.cb_helmet);
        cbVest = findViewById(R.id.cb_vest);
        cbGlasses = findViewById(R.id.cb_glasses);
        cbBelt = findViewById(R.id.cb_belt);
        cbSuit = findViewById(R.id.cb_suit);
        cbGloves = findViewById(R.id.cb_gloves);
        cbToolbox = findViewById(R.id.cb_toolbox);
        cbWelding = findViewById(R.id.cb_welding);
        cbEar = findViewById(R.id.cb_ear);

        CheckBox.OnCheckedChangeListener ruleListener = (v, b) -> updateRules();
        cbMask.setOnCheckedChangeListener(ruleListener);
        cbHelmet.setOnCheckedChangeListener(ruleListener);
        cbVest.setOnCheckedChangeListener(ruleListener);
        cbGlasses.setOnCheckedChangeListener(ruleListener);
        cbBelt.setOnCheckedChangeListener(ruleListener);
        cbSuit.setOnCheckedChangeListener(ruleListener);
        cbGloves.setOnCheckedChangeListener(ruleListener);
        cbToolbox.setOnCheckedChangeListener(ruleListener);
        cbWelding.setOnCheckedChangeListener(ruleListener);
        cbEar.setOnCheckedChangeListener(ruleListener);

        updateRules();

        // Thread başlat
        new Thread(new ClientThread()).start();
    }

    private void updateRules() {
        currentRules = String.format(
                "{\"check_mask\": %b, \"check_helmet\": %b, \"check_belt\": %b, \"check_vest\": %b, "
                        + "\"check_glasses\": %b, \"check_suit\": %b, \"check_gloves\": %b, "
                        + "\"check_toolbox\": %b, \"check_welding\": %b, \"check_ear\": %b}",
                cbMask.isChecked(),
                cbHelmet.isChecked(),
                cbBelt.isChecked(),
                cbVest.isChecked(),
                cbGlasses.isChecked(),
                cbSuit.isChecked(),
                cbGloves.isChecked(),
                cbToolbox.isChecked(),
                cbWelding.isChecked(),
                cbEar.isChecked()
        );
    }

    // --- GÜNCELLENMİŞ ISRARCI THREAD ---
    class ClientThread implements Runnable {
        @Override
        public void run() {
            // DIŞ DÖNGÜ: Bağlantı kopsa bile tekrar başa döner
            while (isRunning) {
                try {
                    // Bağlanmayı Dene
                    Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                    DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                    DataInputStream dataIn = new DataInputStream(socket.getInputStream());

                    // Başarılı olursa kullanıcıya haber ver
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ Bağlandı!", Toast.LENGTH_SHORT).show());

                    // İÇ DÖNGÜ: Veri alışverişi
                    while (isRunning) {
                        // 1. Kural Gönder
                        byte[] jsonBytes = currentRules.getBytes(StandardCharsets.UTF_8);
                        dataOut.writeInt(jsonBytes.length);
                        dataOut.write(jsonBytes);
                        dataOut.flush();

                        // 2. Resim Al
                        int length = dataIn.readInt();
                        if (length > 0) {
                            byte[] imgData = new byte[length];
                            dataIn.readFully(imgData, 0, imgData.length);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);

                            // Ekrana Bas
                            runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                        }
                    }
                    socket.close();
                } catch (Exception e) {
                    // Hata olursa (Bağlantı koparsa veya sunucu kapalıysa)
                    e.printStackTrace();

                    // Kullanıcıya çaktırmadan arka planda bekle ve tekrar dene
                    try {
                        Thread.sleep(2000); // 2 saniye bekle
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "🔄 Bağlantı aranıyor...", Toast.LENGTH_SHORT).show());
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }
}