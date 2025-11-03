package Server;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Server {
    private static final int PORT = 4444;

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Serveur en attente de connexion sur le port " + PORT + "...");
            try (Socket client = server.accept()) {
                System.out.println("Connexion acceptée de : " + client.getInetAddress());
                ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                out.flush();
                ObjectInputStream in = new ObjectInputStream(client.getInputStream());

                SecretKey cleAESClient = generateKey(128);
                byte[] keyBytes = cleAESClient.getEncoded();
                out.writeObject(keyBytes); // envoi de la clé brute
                out.flush();

                boolean connexionFerme = false;
                while (!connexionFerme) {
                    Object obj = in.readObject();
                    if (!(obj instanceof String)) break;
                    String clientEntree = (String) obj;

                    System.out.println("Client : " + clientEntree);

                    out.writeObject("Message Reçu");
                    out.flush();

                    connexionFerme = "bye".equalsIgnoreCase(clientEntree);
                }
                System.out.println("Fin de la transmission serveur.");
            }
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private static SecretKey generateKey(int bits) throws NoSuchAlgorithmException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(bits);
        return kg.generateKey();
    }
}
