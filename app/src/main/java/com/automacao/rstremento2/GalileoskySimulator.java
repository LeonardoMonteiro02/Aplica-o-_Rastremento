package com.automacao.rstremento2;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GalileoskySimulator {

    private static final String TAG = "GalileoskySimulator";
    private static final String SERVER_ADDRESS = "179.131.10.90"; // Endereço do servidor
    private static final int SERVER_PORT = 20018; // Porta do servidor

    private volatile boolean isConnected = false;
    private final LocationService locationService;
    private String cpf;

    private PacketSendingThread sendingThread;
    private PacketSavingThread savingThread;
    private final BlockingQueue<byte[]> packetQueue;

    /**
     * Construtor que inicializa o serviço de localização e a fila de pacotes.
     *
     * @param locationService Instância do serviço de localização.
     */
    public GalileoskySimulator(LocationService locationService) {
        this.locationService = locationService;
        this.packetQueue = new LinkedBlockingQueue<>();
    }

    /**
     * Envia as coordenadas do dispositivo para o servidor, tentando reconectar em caso de falha.
     *
     * @param imei IMEI do dispositivo.
     * @param cpf  CPF associado ao dispositivo.
     * @return true se a conexão foi estabelecida com sucesso, caso contrário, false.
     */
    public boolean sendCoordinates(String imei, String cpf, String placa) {
        byte[] firstPacket = buildFirstPacket(imei);
        Log.d(TAG, "Tentando enviar o primeiro pacote para o servidor...");

        while (!sendPacketToServer(firstPacket, true)) {
            isConnected = false;
            Log.d(TAG, "Falha ao estabelecer a conexão inicial com o servidor. Tentando novamente em 5 segundos...");
            try {
                Thread.sleep(5000); // Espera 5 segundos antes de tentar novamente
            } catch (InterruptedException e) {
                Log.e(TAG, "Erro durante a espera para reconectar: " + e.getMessage());
                Thread.currentThread().interrupt();
                return false; // Retorna false se a thread for interrompida
            }
        }

        isConnected = true;
        Log.d(TAG, "Conexão estabelecida com o servidor.");
        startPacketSavingThread(imei, cpf, placa);
        startPacketSendingThread();
        return true;
    }

    /**
     * Verifica se o simulador está conectado ao servidor.
     *
     * @return true se está conectado, caso contrário, false.
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Define o estado da conexão.
     *
     * @param isConnected Estado da conexão.
     */
    public void setisConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    /**
     * Envia um pacote para o servidor.
     *
     * @param packet        O pacote a ser enviado.
     * @param isFirstPacket Indica se é o primeiro pacote a ser enviado.
     * @return true se o pacote foi enviado com sucesso, caso contrário, false.
     */
    public boolean sendPacketToServer(byte[] packet, boolean isFirstPacket) {
        boolean success = sendPacketToServerInternal(packet, isFirstPacket);
        if (!success && !isFirstPacket) {
            // Verifica se o pacote já está na fila antes de adicioná-lo novamente
            if (!packetQueue.contains(packet)) {
                packetQueue.add(packet);
                Log.d(TAG, "Pacote armazenado no buffer.");
            } else {
                Log.d(TAG, "Pacote já presente no buffer, não adicionando novamente.");
            }
        }
        return success;
    }

    /**
     * Método interno para enviar um pacote para o servidor.
     *
     * @param packet        O pacote a ser enviado.
     * @param isFirstPacket Indica se é o primeiro pacote a ser enviado.
     * @return true se o pacote foi enviado com sucesso, caso contrário, false.
     */
    private boolean sendPacketToServerInternal(byte[] packet, boolean isFirstPacket) {
        boolean success = false;
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             OutputStream outputStream = socket.getOutputStream();
             InputStream inputStream = socket.getInputStream()) {

            Log.d(TAG, "Enviando pacote para o servidor...");
            outputStream.write(packet);
            outputStream.flush();

            byte[] response = new byte[1024];
            int bytesRead = inputStream.read(response);
            if (bytesRead > 0) {
                Log.d(TAG, "Resposta do servidor recebida:");
                Log.d(TAG, bytesToHex(Arrays.copyOf(response, bytesRead)));

                // Verificar CRC
                short crcLocal = (short) (((packet[packet.length - 2] & 0xFF) << 8) | (packet[packet.length - 1] & 0xFF));
                short crcServer = (short) (((response[1] & 0xFF) << 8) | (response[2] & 0xFF));

                if (crcLocal == crcServer) {
                    Log.d(TAG, "CRC válido. Resposta do servidor é válida.");
                    success = true;
                } else {
                    Log.d(TAG, "CRC inválido. Resposta do servidor não é válida.");
                }
            } else {
                Log.d(TAG, "Nenhuma resposta do servidor.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Erro ao enviar o pacote: " + e.getMessage());
        }

        isConnected = success;  // Atualiza o estado da conexão com base no sucesso do envio
        return success;
    }

    /**
     * Inicia a thread de salvamento de pacotes.
     *
     * @param imei IMEI do dispositivo.
     * @param cpf  CPF associado ao dispositivo.
     */
    private void startPacketSavingThread(String imei, String cpf,String placa) {
        savingThread = new PacketSavingThread(this, locationService, imei, cpf,placa);
        savingThread.start();
    }

    /**
     * Inicia a thread de envio de pacotes.
     */
    private void startPacketSendingThread() {
        if (sendingThread == null || !sendingThread.isAlive()) {
            sendingThread = new PacketSendingThread(this);
            sendingThread.start();
        }
    }

    /**
     * Retorna a fila de pacotes.
     *
     * @return A fila de pacotes.
     */
    public BlockingQueue<byte[]> getPacketQueue() {
        return packetQueue;
    }

    /**
     * Adiciona um pacote de dados à fila.
     *
     * @param packet O pacote de dados a ser adicionado.
     */
    protected void addDataPacket(byte[] packet) {
        packetQueue.add(packet);
        Log.d(TAG, "A Lista de dados tem tamanho de: ------> " + packetQueue.size());
    }

    /**
     * Método público para reconectar ao servidor.
     *
     * @return true se a reconexão foi bem-sucedida, caso contrário, false.
     */
    public boolean reconnectToServer() {
        byte[] firstPacket = buildFirstPacket("IMEI"); // Usar o IMEI apropriado
        Log.d(TAG, "Tentando reconectar ao servidor...");
        if (sendPacketToServer(firstPacket, true)) {
            isConnected = true;
            Log.d(TAG, "Reconexão estabelecida com sucesso.");
            return true;
        } else {
            isConnected = false;
            Log.d(TAG, "Falha ao reconectar.");
            return false;
        }
    }

    /**
     * Constrói o primeiro pacote a ser enviado ao servidor.
     *
     * @param imei IMEI do dispositivo.
     * @return O pacote construído.
     */
    private byte[] buildFirstPacket(String imei) {
        byte[] imeiBytes = imei.getBytes();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0x01); // Header
        buffer.putShort((short) 0); // Placeholder para o comprimento

        // Tags de versão do hardware e firmware
        buffer.put((byte) 0x01); // Tag 1 para Hardware version
        buffer.put((byte) 0x82); // Versão do hardware fictícia
        buffer.put((byte) 0x02); // Tag 2 para Firmware version
        buffer.put((byte) 0x15); // Versão do firmware fictícia

        // Tag de IMEI
        buffer.put((byte) 0x03); // Tag 3 para IMEI
        buffer.put(imeiBytes);

        int length = buffer.position() - 3; // Exclui o header e o próprio comprimento
        buffer.putShort(1, (short) length); // Insere o comprimento no lugar correto

        byte[] packetWithoutChecksum = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(packetWithoutChecksum);

        short crc = calculateCRC16Modbus(packetWithoutChecksum);
        buffer.putShort(crc);

        byte[] packetWithChecksum = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(packetWithChecksum);

        return packetWithChecksum;
    }

    /**
     * Calcula o CRC16 Modbus para um dado array de bytes.
     *
     * @param data Os dados para os quais o CRC deve ser calculado.
     * @return O valor do CRC calculado.
     */
    private short calculateCRC16Modbus(byte[] data) {
        int crc = 0xFFFF;
        for (int i = 0; i < data.length; i++) {
            crc ^= (int) data[i] & 0xFF;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc = crc >> 1;
                }
            }
        }
        return (short) crc;
    }

    /**
     * Converte um array de bytes para uma string hexadecimal.
     *
     * @param bytes O array de bytes a ser convertido.
     * @return A string hexadecimal resultante.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Para as threads de salvamento e envio de pacotes.
     */
    public void stopThreads() {
        savingThread.shutdown();
        sendingThread.shutdown();
    }
}