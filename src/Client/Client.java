package Client;

import Cryptage.AES;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.Scanner;

public class Client {
    private static final String SERVEUR_ADRESSE = "localhost";
    private static final int PORT = 4444;

    public static void main(String[] args) {
        try (Socket serveur = new Socket(SERVEUR_ADRESSE, PORT)) {
            System.out.println("Connecté au serveur !");

            ObjectOutputStream out = new ObjectOutputStream(serveur.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(serveur.getInputStream());

            Object kobj = in.readObject();
            byte[] keyBytes = (byte[]) kobj;

            SecretKey cleAESClient = new SecretKeySpec(keyBytes, "AES");

            System.out.println("Clé AES reçue : " + Base64.getEncoder().encodeToString(keyBytes));

            Scanner scanner = new Scanner(System.in);

            new Thread(() -> {
                try {
                    while (true) {
                        String messageChiffre = (String) in.readObject();
                        String message = AES.decrypteAES(messageChiffre, cleAESClient);
                        System.out.println("\n[Message reçu] : " + message);
                        System.out.print("Votre message > ");
                    }
                } catch (Exception e) {
                    System.out.println("Connexion fermée.");
                }
            }).start();

            boolean connexionFerme = false;

            while (!connexionFerme) {

                System.out.print("Votre message > ");
                String msg = scanner.nextLine();
                String msgChiffre = AES.crypteAES(msg, cleAESClient);
                out.writeObject(msgChiffre);
                out.flush();

                connexionFerme = msg.equalsIgnoreCase("bye");
            }
            System.out.println("Fin de la transmission client.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
