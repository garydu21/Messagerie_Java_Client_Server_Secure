package Client;

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
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            System.out.println("Clé reçue (" + keyBytes.length + " octets).");

            Scanner scanner = new Scanner(System.in);
            boolean connexionFerme = false;

            while (!connexionFerme) {
                System.out.print("Entrez votre message : ");
                String clientSortie = scanner.nextLine();

                out.writeObject(clientSortie);
                out.flush();

                Object reply = in.readObject();
                String serverEntree = (reply instanceof String) ? (String) reply : String.valueOf(reply);
                System.out.println("Serveur : " + serverEntree);

                connexionFerme = "bye".equalsIgnoreCase(clientSortie);
            }
            System.out.println("Fin de la transmission client.");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
