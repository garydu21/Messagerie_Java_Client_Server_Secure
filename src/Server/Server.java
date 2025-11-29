package Server;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final int PORT = 4444;

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
