package com.automacao.rstremento2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class LocationService extends Thread {
    private final FusedLocationProviderClient fusedLocationClient;
    private final Context context;
    private LocationCallback locationCallback;
    private GnssStatus.Callback gnssStatusCallback;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private boolean isRunning = false;
    private LocationUpdateListener listener;

    // Atributos de localização
    private double latitude;
    private double longitude;
    private double altitude;
    private float speed;
    private int satellitesConnected;

    /**
     * Interface para listener de atualizações de localização.
     */
    public interface LocationUpdateListener {
        void onLocationUpdate(double latitude, double longitude, double altitude, float speed, int satellites);
    }

    /**
     * Construtor que inicializa o serviço de localização.
     *
     * @param context Contexto da aplicação.
     */
    public LocationService(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Define o listener para receber atualizações de localização.
     *
     * @param listener O listener a ser definido.
     */
    public void setListener(LocationUpdateListener listener) {
        this.listener = listener;
    }

    /**
     * Solicita permissões de localização ao usuário.
     *
     * @param activity A atividade atual.
     */
    public void requestLocationPermissions(Activity activity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Método principal da thread, responsável por iniciar e gerenciar atualizações de localização.
     */
    @Override
    public void run() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Se as permissões não estão disponíveis, não inicie as atualizações de localização
            return;
        }

        isRunning = true;

        new Handler(Looper.getMainLooper()).post(() -> {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setInterval(0);
            locationRequest.setFastestInterval(0);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null) {
                        return;
                    }
                    for (Location location : locationResult.getLocations()) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        altitude = location.getAltitude();
                        speed = location.getSpeed() * 4;
                        if (listener != null) {
                            listener.onLocationUpdate(latitude, longitude, altitude, speed, satellitesConnected);
                        }
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

            // Configura o callback para obter informações de GNSS
            setupGnssStatusCallback();
        });

        while (isRunning) {
            try {
                // Sleep para reduzir o consumo de CPU
                Thread.sleep(100);
            } catch (InterruptedException e) {
                isRunning = false;
                break;
            }
        }

        // Para as atualizações de localização quando a thread é parada
        stopLocationUpdates();
    }

    /**
     * Obtém a última localização conhecida.
     */
    @SuppressLint("MissingPermission")
    public void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Se as permissões não estão disponíveis, não tente obter a última localização conhecida
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        altitude = location.getAltitude();
                        speed = location.getSpeed() * 4;
                        if (listener != null) {
                            listener.onLocationUpdate(latitude, longitude, altitude, speed, satellitesConnected);
                        }
                    }
                });
    }

    /**
     * Para as atualizações de localização e o callback de GNSS.
     */
    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (gnssStatusCallback != null) {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            gnssStatusCallback = null;
        }
        isRunning = false;
    }

    // Métodos públicos para obter os dados de localização
    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public float getSpeed() {
        return speed;
    }

    public int getSatellitesConnected() {
        return satellitesConnected;
    }

    /**
     * Configura o callback para receber atualizações de status do GNSS.
     */
    @SuppressLint("MissingPermission")
    private void setupGnssStatusCallback() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                super.onSatelliteStatusChanged(status);
                satellitesConnected = status.getSatelliteCount();
            }
        };
        new Handler(Looper.getMainLooper()).post(() -> locationManager.registerGnssStatusCallback(gnssStatusCallback));
    }
}