package Cryptage;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Classe de chiffrement et dechiffrement AES-128.
 * Fournit des methodes statiques pour generer des cles et chiffrer/dechiffrer des messages.
 *
 * @author Chris - Angel
 * @version 2.0
 */
public class AES {

    /**
     * Genere une cle AES de taille specifiee.
     *
     * @param bits Taille de la cle (128, 192 ou 256 bits)
     * @return La cle AES generee
     * @throws Exception Si la generation echoue
     */
    public static SecretKey genererCle(int bits) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(bits);
        return kg.generateKey();
    }

    /**
     * Chiffre un message avec AES.
     * Le message chiffre est encode en Base64 pour faciliter la transmission.
     *
     * @param message Le message en clair
     * @param key La cle AES
     * @return Le message chiffre en Base64
     * @throws Exception Si le chiffrement echoue
     */
    public static String crypteAES(String message, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cryptageByte = cipher.doFinal(message.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(cryptageByte);
    }

    /**
     * Dechiffre un message AES.
     *
     * @param message Le message chiffre en Base64
     * @param key La cle AES
     * @return Le message en clair
     * @throws Exception Si le dechiffrement echoue (mauvaise cle ou message corrompu)
     */
    public static String decrypteAES(String message, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptByte = cipher.doFinal(Base64.getDecoder().decode(message));
        return new String(decryptByte, "UTF-8");
    }
}