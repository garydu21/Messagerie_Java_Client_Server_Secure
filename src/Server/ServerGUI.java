package Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Serveur pour l'interface graphique
 */
public class ServerGUI {
    private static final int PORT = 4444;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   SERVEUR DE CHAT SÉCURISÉ (GUI)      ║");
        System.out.println("║   Port: " + PORT + "                          ║");
        System.out.println("║   Chiffrement: AES-128                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();

        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("🟢 Serveur en attente de connexions...\n");

            while (true) {
                Socket client = server.accept();
                System.out.println("✓ Nouveau client connecté: " + client.getInetAddress());

                // Créer un thread avec le gestionnaire GUI
                new Thread(new gestionnaireClientGUI(client)).start();
            }

        } catch (IOException e) {
            System.err.println("❌ Erreur serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }
}