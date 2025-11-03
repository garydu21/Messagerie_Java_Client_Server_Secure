package Client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String SERVEUR_ADRESSE = "localhost";
    private static final int PORT = 4444;

    public static void main(String[] args) {
        try (Socket serveur = new Socket(SERVEUR_ADRESSE, PORT)) {
            // Connexion au serveur
            System.out.println("Connecté au serveur !");

            // Communication avec le serveur
            BufferedReader in = new BufferedReader(new InputStreamReader(serveur.getInputStream()));
            PrintWriter out = new PrintWriter(serveur.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            boolean connexionFerme = false;

            while (!connexionFerme) {
                System.out.print("Entrez votre message : ");

                String clientSortie = scanner.nextLine();

                out.println(clientSortie);

                String serverEntree = in.readLine(); // Réponse du serveur

                System.out.println(serverEntree);

                connexionFerme = clientSortie.equals("bye");
            }
            System.out.println("Fin de la transmission client.");
            // Fin

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}