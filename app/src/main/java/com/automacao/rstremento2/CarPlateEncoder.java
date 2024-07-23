package com.automacao.rstremento2;

import android.util.Log;

public class CarPlateEncoder {

    // Método para codificar a placa em 4 bytes
    public static int encode(String plate) {
        if (plate == null || plate.length() != 7) {
            throw new IllegalArgumentException("Placa inválida");
        }

        // Extraindo caracteres
        char c1 = plate.charAt(0);
        char c2 = plate.charAt(1);
        char c3 = plate.charAt(2);
        char c4 = plate.charAt(4); // Note que a posição é 4 por causa do número no meio
        char n1 = plate.charAt(3);
        char n2 = plate.charAt(5);
        char n3 = plate.charAt(6);

        // Convertendo letras (A-Z) para valores (0-25)
        int l1 = c1 - 'A';
        int l2 = c2 - 'A';
        int l3 = c3 - 'A';
        int l4 = c4 - 'A';

        // Convertendo números (0-9) para valores inteiros
        int num1 = n1 - '0';
        int num2 = n2 - '0';
        int num3 = n3 - '0';

        // Codificando os valores em um inteiro de 32 bits
        int encoded = (l1 << 27) | (l2 << 22) | (l3 << 17) | (num1 << 13) |
                (l4 << 8) | (num2 << 4) | num3;

        // Log do CPF descomprimido
        Log.d("CARConverter", "Placa comprimido: " + encoded);
        return encoded;
    }

    // Método para decodificar os 4 bytes de volta para a placa
    public static String decode(int encoded) {
        // Extraindo valores das letras e números
        int l1 = (encoded >> 27) & 0x1F;
        int l2 = (encoded >> 22) & 0x1F;
        int l3 = (encoded >> 17) & 0x1F;
        int num1 = (encoded >> 13) & 0xF;
        int l4 = (encoded >> 8) & 0x1F;
        int num2 = (encoded >> 4) & 0xF;
        int num3 = encoded & 0xF;

        // Convertendo de volta para caracteres
        char c1 = (char) (l1 + 'A');
        char c2 = (char) (l2 + 'A');
        char c3 = (char) (l3 + 'A');
        char c4 = (char) (l4 + 'A');
        char n1 = (char) (num1 + '0');
        char n2 = (char) (num2 + '0');
        char n3 = (char) (num3 + '0');

        // Construindo a placa decodificada
        return "" + c1 + c2 + c3 + n1 + c4 + n2 + n3;
    }


}

