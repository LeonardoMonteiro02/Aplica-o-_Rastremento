package com.automacao.rstremento2;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CPFConverter {

    // Mapas para armazenar a associação entre CPF original e sua forma comprimida para demonstração
    private static final Map<String, byte[]> cpfToCompressed = new HashMap<>();
    private static final Map<String, String> compressedToCpf = new HashMap<>();

    /**
     * Comprime um CPF usando SHA-256 e retorna os primeiros 4 bytes do hash.
     *
     * @param cpf CPF em formato de string.
     * @return Array de bytes representando o CPF comprimido.
     * @throws NoSuchAlgorithmException Se o algoritmo SHA-256 não estiver disponível.
     */
    public static byte[] compressCPF(String cpf) throws NoSuchAlgorithmException {
        // Obtém uma instância do MessageDigest para SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // Calcula o hash do CPF
        byte[] hash = digest.digest(cpf.getBytes());

        // Obtém os primeiros 4 bytes do hash
        byte[] compressed = Arrays.copyOfRange(hash, 0, 4);

        // Armazena o mapeamento do CPF original para o comprimido
        cpfToCompressed.put(cpf, compressed);
        compressedToCpf.put(Arrays.toString(compressed), cpf);

        // Log dos bytes comprimidos
        Log.d("CPFConverter", "CPF comprimido: " + bytesToHex(compressed));

        return compressed;
    }

    /**
     * Descomprime um array de bytes comprimido para seu CPF original.
     *
     * @param compressed Array de bytes representando o CPF comprimido.
     */
    public static void decompressCPF(byte[] compressed) {
        // Obtém o CPF original do mapa usando os bytes comprimidos como chave
        String cpf = compressedToCpf.get(Arrays.toString(compressed));

        // Log do CPF descomprimido
        Log.d("CPFConverter", "CPF descomprimido: " + cpf);
    }

    /**
     * Converte um array de bytes em uma string hexadecimal.
     *
     * @param bytes Array de bytes a ser convertido.
     * @return String representando os bytes em formato hexadecimal.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}