package Client;

import Cryptage.AES;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.Scanner;

/**
 * Client de chat en mode console.
 * Se connecte au serveur, recoit une cle AES et echange des messages chiffres.
 *
 * Choix cryptographie symetrique (AES) :
 * - Beaucoup plus rapide que RSA pour chiffrer les messages
 * - Demander dans l'enonce
 *
 * @author Chris - Angel
 * @version 1.0
 */
public class Client {
    /**
     * Adresse du serveur (localhost pour tests locaux).
     */
    private static final String SERVEUR_ADRESSE = "localhost";

    /**
     * Port du serveur (doit correspondre au port serveur).
     */
    private static final int PORT = 4444;

    /**
     * Point d'entree du client.
     * Se connecte au serveur, recoit la cle AES, puis lance deux threads :
     * - Thread principal : Lecture clavier et envoi messages
     * - Thread secondaire : Reception messages du serveur
     *
     * @param args Arguments non utilises
     */
    public static void main(String[] args) {
        try (Socket serveur = new Socket(SERVEUR_ADRESSE, PORT)) {
            System.out.println("Connecté au serveur !");

            ObjectOutputStream out = new ObjectOutputStream(serveur.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(serveur.getInputStream());

            // Reception de la cle AES du serveur
            Object kobj = in.readObject();
            byte[] keyBytes = (byte[]) kobj;

            SecretKey cleAESClient = new SecretKeySpec(keyBytes, "AES");

            System.out.println("Clé AES reçue : " + Base64.getEncoder().encodeToString(keyBytes));

            Scanner scanner = new Scanner(System.in);

            // Thread de reception des messages
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

            // Thread principal : envoi des messages
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