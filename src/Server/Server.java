package Server;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Serveur de chat multi-clients en mode console.
 * Ecoute sur le port 4444 et cree un thread dedie pour chaque client.
 *
 * @author Chris - Angel
 * @version 1.0
 */
public class Server {
    /**
     * Port d'ecoute du serveur.
     */
    private static final int PORT = 4444;

    /**
     * Point d'entree du serveur.
     * Cree une ServerSocket et attend les connexions dans une boucle infinie.
     * Pour chaque client, un nouveau thread gestionnaireClient est lance.
     *
     * @param args Arguments de la ligne de commande (non utilises)
     */
    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Serveur en attente de connexions...");

            while (true) {
                Socket client = server.accept();
                System.out.println("Nouveau client connecté : " + client.getInetAddress());

                new Thread(new gestionnaireClient(client)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}