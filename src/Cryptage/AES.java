package Cryptage;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

public class AES {

    public AES (){

    }

    public static SecretKey genererCle(int bits) throws Exception{
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(bits);
        return kg.generateKey();
    }

    public static String crypteAES(String message, SecretKey key) throws Exception{
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cryptageByte = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(cryptageByte);
    }

    public static String decrypteAES(String message, SecretKey key) throws Exception{
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptByte = cipher.doFinal(Base64.getDecoder().decode(message));
        byte[] cryptageByte = decryptByte;
        return new String(cryptageByte);
    }
}
