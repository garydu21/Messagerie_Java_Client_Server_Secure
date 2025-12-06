package Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Serveur de chat avec support des salons prives.
 * Version amelioree supportant plusieurs salons avec cles AES independantes.
 * Chaque salon a sa propre cle pour isoler cryptographiquement les messages.
 *
 * @author Chris - Angel
 * @version 2.0
 */
public class ServerGUI {
    /**
     * Port d'ecoute du serveur (identique a la version console).
     */
    private static final int PORT = 4444;

    /**
     * Demarre le serveur graphique.
     * Affiche une banniere puis accepte les connexions entrantes.
     * Utilise gestionnaireClientGUI pour gerer les salons et messages prives.
     *
     * @param args Arguments non utilises
     */
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   SERVEUR DE CHAT SÉCURISÉ (GUI)      ║");
        System.out.println("║   Port: " + PORT + "                          ║");
        System.out.println("║   Chiffrement: AES-128                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();

        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Serveur en attente de connexions...\n");

            while (true) {
                Socket client = server.accept();
                System.out.println("Nouveau client connecté: " + client.getInetAddress());

                new Thread(new gestionnaireClientGUI(client)).start();
            }

        } catch (IOException e) {
            System.err.println("Erreur serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }
}