package Server;

import java.io.*;
import java.net.*;

public class Server {
    private static final int PORT = 4444;

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(PORT)) {
            // Initialisation du serveur
            System.out.println("Serveur en attente de connexion sur le port " + PORT + "...");
            Socket client = server.accept();
            System.out.println("Connexion acceptée de : " + client.getInetAddress());

            // Initialisation des flux
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);

            boolean connexionFerme = false;

            while (!connexionFerme) {

                String clientEntree = in.readLine();

                System.out.println("Client : " + clientEntree);

                out.println("Message Reçu");

                connexionFerme = clientEntree.equals("bye");

            }
            System.out.println("Fin de la transmission serveur.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}