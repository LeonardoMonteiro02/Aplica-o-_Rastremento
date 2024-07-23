package com.automacao.rstremento2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private LocationService locationService;
    private GoogleMap mMap;
    private GalileoskySimulator galileoskySimulator;
    private TextView latitude;
    private TextView longitude;
    private TextView speed;
    private TextView satelite;
    private boolean sendingLocation = false;

    /**
     * Método chamado quando a atividade é criada.
     * Inicializa os serviços e configura a interface do usuário.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa o LocationService
        locationService = new LocationService(this);

        // Obtém o fragmento do mapa e inicializa o mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(googleMap -> {
            mMap = googleMap;
            // Opcionalmente, você pode personalizar as configurações do mapa aqui
        });

        // Inicializa o GalileoskySimulator
        galileoskySimulator = new GalileoskySimulator(locationService);

        // Configura os TextViews
        latitude = findViewById(R.id.latitude);
        longitude = findViewById(R.id.longitude);
        speed = findViewById(R.id.speed);
        satelite = findViewById(R.id.satelite);

        // Configura o botão para iniciar ou parar o processo de localização
        Button buttonSendLocation = findViewById(R.id.button_start_location);
        buttonSendLocation.setOnClickListener(v -> {
            if (!sendingLocation) {
                // Verifica se as permissões de localização foram concedidas
                if (checkLocationPermission()) {
                    startLocationService();
                    sendingLocation = true;
                    buttonSendLocation.setText("Parar Envio"); // Altera o texto do botão
                } else {
                    requestLocationPermissions();
                }
            } else {
                stopLocationService();
                sendingLocation = false;
                buttonSendLocation.setText("Enviar Localização"); // Altera o texto do botão
                galileoskySimulator.stopThreads();
                finishAffinity();
                System.exit(0);
            }
        });
    }

    /**
     * Verifica se a permissão de localização foi concedida.
     *
     * @return true se a permissão foi concedida, caso contrário, false.
     */
    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Solicita permissões de localização ao usuário.
     */
    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    /**
     * Método chamado quando o usuário responde a uma solicitação de permissão.
     * Verifica se a permissão foi concedida e inicia o serviço de localização se for o caso.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Atualiza a interface do usuário com os dados de localização recebidos.
     *
     * @param satellites Número de satélites.
     * @param lat Latitude.
     * @param longi Longitude.
     * @param vel Velocidade.
     */
    private void updateUI(int satellites, double lat, double longi, float vel) {
        runOnUiThread(() -> {
            satelite.setText("Satelites: " + satellites);
            latitude.setText("Latitude: " + lat);
            longitude.setText("Longitude: " + longi);
            speed.setText("Speed: " + String.format("%.2f", vel));

            if (mMap != null) {
                LatLng currentLatLng = new LatLng(lat, longi);
                mMap.clear(); // Limpa marcadores anteriores
                mMap.addMarker(new MarkerOptions().position(currentLatLng).title("Current Location"));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15)); // Ajusta o nível de zoom conforme necessário
            }
        });
    }

    /**
     * Inicia o serviço de localização e atualiza a interface do usuário com os dados de localização.
     */
    private void startLocationService() {
        locationService.setListener((latitude, longitude, altitude, speed, satellites) -> {
          /*  Log.d("LocationService", "Latitude: " + latitude);
            Log.d("LocationService", "Longitude: " + longitude);
            Log.d("LocationService", "Altitude: " + altitude);
            Log.d("LocationService", "Speed: " + speed);
            Log.d("LocationService", "Satellites: " + satellites);*/
            updateUI(satellites, latitude, longitude, speed);
        });
        locationService.start();
        startGalileoskySimulator();
    }

    /**
     * Inicia o simulador Galileosky em uma nova thread.
     */
    private void startGalileoskySimulator() {
        new Thread(() -> {
            if (galileoskySimulator.sendCoordinates("357138166785014", "12565696908", "ACC1D23")) {
                Log.d("MainActivity", "Conexão inicial estabelecida.");
            } else {
                Log.d("MainActivity", "Falha ao estabelecer a conexão inicial.");
            }
        }).start();
    }

    /**
     * Para o serviço de localização.
     */
    private void stopLocationService() {
        locationService.stopLocationUpdates();
        // Aqui você pode adicionar lógica adicional para parar o GalileoskySimulator se necessário
    }

    /**
     * Método chamado quando a atividade é destruída.
     * Para o serviço de localização.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationService();
    }
}











