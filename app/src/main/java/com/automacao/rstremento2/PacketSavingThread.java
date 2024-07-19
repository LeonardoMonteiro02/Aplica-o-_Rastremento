package com.automacao.rstremento2;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.NoSuchAlgorithmException;

public class PacketSavingThread extends Thread {
    private static final String TAG = "PacketSavingThread";
    private final String imei;
    private final LocationService locationService;
    private final GalileoskySimulator simulator;
    private final CPFConverter cpfConverter;
    private final String cpf;
    private boolean running = true;

    /**
     * Construtor que inicializa a thread de salvamento de pacotes.
     *
     * @param simulator       Instância do simulador Galileosky.
     * @param locationService Serviço de localização para obter dados de GPS.
     * @param imei            IMEI do dispositivo.
     * @param cpf             CPF a ser convertido e adicionado aos pacotes.
     */
    public PacketSavingThread(GalileoskySimulator simulator, LocationService locationService, String imei, String cpf) {
        this.imei = imei;
        this.locationService = locationService;
        this.simulator = simulator;
        this.cpfConverter = new CPFConverter();
        this.cpf = cpf;
    }

    /**
     * Método principal da thread, responsável por criar e salvar pacotes de dados.
     */
    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(10000); // Espera 10 segundos
                int satellites = locationService.getSatellitesConnected();
                double latitude = locationService.getLatitude();
                double longitude = locationService.getLongitude();
                double altitude = locationService.getAltitude();
                float speed = locationService.getSpeed();
                String message = "Localização atualizada - Lat: " + latitude + ", Long: " + longitude + ", Alt: " + altitude + ", Vel: " + speed + ", Sate: " + satellites;
                Log.d(TAG, message);
                byte[] packet = buildPacket(latitude, longitude, altitude, speed, satellites);
                simulator.addDataPacket(packet);

                // Logar o conteúdo do pacote
                StringBuilder sb = new StringBuilder();
                for (byte b : packet) {
                    sb.append(String.format("%02X ", b));
                }
                Log.d(TAG, "Conteúdo do pacote: " + sb.toString());

            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrompida: " + e.getMessage());
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

    /**
     * Constrói um pacote de dados com as informações fornecidas.
     *
     * @param latitude  Latitude atual.
     * @param longitude Longitude atual.
     * @param altitude  Altitude atual.
     * @param speed     Velocidade atual.
     * @param satellites Número de satélites conectados.
     * @return Pacote de dados em bytes.
     */
    private byte[] buildPacket(double latitude, double longitude, double altitude, float speed, int satellites) {
        byte[] imeiBytes = imei.getBytes();
        int timestamp = getCurrentTimestamp();

        ByteBuffer buffer = ByteBuffer.allocate(2048);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0x01); // Header
        buffer.putShort((short) 0); // Placeholder para o comprimento

        // Tag de IMEI
        buffer.put((byte) 0x03); // Tag 3 para IMEI
        buffer.put(imeiBytes);

        // Tag de timestamp
        buffer.put((byte) 0x20); // Tag 0x20 para timestamp
        buffer.putInt(timestamp);

        // Tag de localização
        buffer.put((byte) 0x30); // Tag 0x30 para dados de localização
        buffer.put(convertCoordinatesToBytes(latitude, longitude, satellites));

        // Tag de velocidade
        buffer.put((byte) 0x33); // Tag 0x33 para velocidade
        buffer.putInt(convertSpeedToBytes(speed));

        // Tag de altitude
        buffer.put((byte) 0x34); // Tag 0x34 para altitude
        buffer.put(convertAltitudeToBytes(altitude));

        // Tag de CPF
        buffer.put((byte) 0x90); // Tag 0x90 para CPF
        try {
            buffer.put(cpfConverter.compressCPF(cpf));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // Calculando o comprimento do pacote
        int length = buffer.position() - 3; // Exclui o header e o próprio comprimento
        buffer.putShort(1, (short) length); // Insere o comprimento no lugar correto

        // Calculando o CRC do pacote
        byte[] packetWithoutChecksum = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(packetWithoutChecksum);

        short crc = calculateCRC16Modbus(packetWithoutChecksum);
        buffer.putShort(crc);

        // Copiando o pacote final com o CRC calculado
        byte[] packetWithChecksum = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(packetWithChecksum);

        return packetWithChecksum;
    }

    /**
     * Obtém o timestamp atual em segundos.
     *
     * @return Timestamp atual em segundos.
     */
    private int getCurrentTimestamp() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    /**
     * Converte coordenadas e número de satélites em um array de bytes.
     *
     * @param latitude   Latitude a ser convertida.
     * @param longitude  Longitude a ser convertida.
     * @param satellites Número de satélites conectados.
     * @return Coordenadas e número de satélites em bytes.
     */
    private byte[] convertCoordinatesToBytes(double latitude, double longitude, int satellites) {
        int latitudeInt = (int) (latitude * 1e6);
        int longitudeInt = (int) (longitude * 1e6);

        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) satellites); // Número de satélites
        buffer.putInt(latitudeInt);
        buffer.putInt(longitudeInt);

        return buffer.array();
    }

    /**
     * Converte altitude em um array de bytes.
     *
     * @param altitude Altitude a ser convertida.
     * @return Altitude em bytes.
     */
    private static byte[] convertAltitudeToBytes(double altitude) {
        short altitudeShort = (short) altitude;

        byte[] bytes = new byte[2];
        bytes[0] = (byte) (altitudeShort); // Byte menos significativo
        bytes[1] = (byte) (altitudeShort >> 8); // Byte mais significativo

        return bytes;
    }

    /**
     * Converte velocidade em um valor inteiro de bytes.
     *
     * @param speed Velocidade a ser convertida.
     * @return Velocidade em bytes.
     */
    private int convertSpeedToBytes(float speed) {
        return (int) speed * 10;
    }

    /**
     * Calcula o CRC16 Modbus de um array de bytes.
     *
     * @param data Dados para os quais calcular o CRC.
     * @return Valor do CRC calculado.
     */
    private short calculateCRC16Modbus(byte[] data) {
        int crc = 0xFFFF;
        for (byte datum : data) {
            crc ^= (datum & 0xFF);
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
}
