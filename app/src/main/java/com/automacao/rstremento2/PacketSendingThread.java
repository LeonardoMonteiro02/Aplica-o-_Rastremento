package com.automacao.rstremento2;

import android.util.Log;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PacketSendingThread extends Thread {

    private static final String TAG = "PacketSendingThread";
    private final GalileoskySimulator galileoskySimulator;
    private final BlockingQueue<byte[]> packetQueue;
    private boolean running = true;

    /**
     * Construtor que inicializa a thread de envio de pacotes.
     *
     * @param galileoskySimulator Instância do simulador Galileosky.
     */
    public PacketSendingThread(GalileoskySimulator galileoskySimulator) {
        this.galileoskySimulator = galileoskySimulator;
        this.packetQueue = galileoskySimulator.getPacketQueue();
    }

    /**
     * Método principal da thread, responsável por enviar pacotes ao servidor.
     */
    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (galileoskySimulator.isConnected()) {
                    byte[] packet = packetQueue.peek();
                    if (packet != null) {
                        boolean success = galileoskySimulator.sendPacketToServer(packet, false);
                        if (success) {
                            packetQueue.poll();  // Remove o pacote da fila apenas se for enviado com sucesso
                            Log.d(TAG, "Pacote enviado ao servidor e removido da fila.");
                        } else {
                            Log.d(TAG, "Falha ao enviar pacote, permanecendo na fila.");
                        }
                    } else {
                        Log.d(TAG, "Nenhum pacote de dados disponível.");
                    }
                } else {
                    Log.d(TAG, "Não conectado ao servidor. Tentando reconectar...");
                    while (!galileoskySimulator.reconnectToServer()) {
                        Log.d(TAG, "Tentativa de reconexão falhou. Tentando novamente em 5 segundos...");
                        Thread.sleep(5000); // Espera 5 segundos antes de tentar reconectar novamente
                    }
                    Log.d(TAG, "Reconexão bem-sucedida.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao enviar pacote: " + e.getMessage());
            }

            try {
                Thread.sleep(2000);  // Espera 2 segundos antes de enviar o próximo pacote
            } catch (InterruptedException e) {
                Log.d(TAG, "Thread interrompida durante espera.");
                Thread.currentThread().interrupt();
            }
        }
        Log.d(TAG, "Thread finalizada.");
    }

    /**
     * Para a execução da thread de forma segura.
     */
    public void shutdown() {
        running = false;
        interrupt(); // Interrompe a thread se estiver dormindo
    }
}