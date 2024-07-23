package com.automacao.rstremento2;

import android.location.Location;
import java.util.LinkedList;
import java.util.Queue;

public class GpsSpeedFilter {
    private static final float MIN_SPEED = 1.0f; // Velocidade mínima em m/s
    private static final float MIN_DISTANCE = 5.0f; // Distância mínima em metros
    private static final int MAX_QUEUE_SIZE = 5; // Tamanho da janela da média móvel

    private Queue<Float> speedQueue = new LinkedList<>();
    private Location lastLocation = null;

    public float getFilteredSpeed(Location currentLocation) {
        float currentSpeed = currentLocation.getSpeed(); // Velocidade diretamente do GPS

        if (lastLocation == null) {
            lastLocation = currentLocation;
            return 0;
        }

        // Verifica a distância e o tempo entre a localização atual e a anterior
        float distance = lastLocation.distanceTo(currentLocation);
        long timeDelta = (currentLocation.getTime() - lastLocation.getTime()) / 1000; // Tempo em segundos

        if (timeDelta == 0 || distance < MIN_DISTANCE) {
            currentSpeed = 0; // Se o deslocamento for pequeno ou o tempo entre atualizações for muito curto
        }

        // Filtro de velocidade mínima
        if (currentSpeed < MIN_SPEED) {
            currentSpeed = 0;
        }

        // Adiciona a velocidade na fila da média móvel
        if (speedQueue.size() == MAX_QUEUE_SIZE) {
            speedQueue.poll();
        }
        speedQueue.add(currentSpeed);

        // Calcula a média móvel
        float averageSpeed = 0;
        for (float s : speedQueue) {
            averageSpeed += s;
        }
        averageSpeed /= speedQueue.size();

        // Atualiza a última localização
        lastLocation = currentLocation;

        // Retorna a velocidade média filtrada em km/h
        return averageSpeed * 3.6f; // Converte de m/s para km/h
    }
}
