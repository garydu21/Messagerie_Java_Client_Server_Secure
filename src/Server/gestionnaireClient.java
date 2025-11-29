package Server;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import Cryptage.AES;

public class gestionnaireClient implements Runnable {

    private final Socket client;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private SecretKey cleAESClient;
    private static final ArrayList<gestionnaireClient> clients = new ArrayList<>();

    public gestionnaireClient(Socket socket) {
        this.client = socket;
        synchronized (clients) {
            clients.add(this);
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(client.getOutputStream());
            out.flush();
            in = new ObjectInputStream(client.getInputStream());

            cleAESClient = AES.genererCle(128);
            out.writeObject(cleAESClient.getEncoded());
            out.flush();

            System.out.println("Clé envoyée au client : " + Base64.getEncoder().encodeToString(cleAESClient.getEncoded()));

            boolean connexionFerme = false;

            while (!connexionFerme) {

                String messageChiffre = (String) in.readObject();
                String message = AES.decrypteAES(messageChiffre, cleAESClient);

                System.out.println("\n===== MESSAGE DU CLIENT =====");
                System.out.println("Chiffré   : " + messageChiffre);
                System.out.println("Déchiffré : " + message);

                broadcast(message,this);

                connexionFerme = message.equalsIgnoreCase("bye");
            }

            deconnexion();

        } catch (Exception e) {
            System.out.println("Erreur avec client : " + e.getMessage());
        }
    }

    private void broadcast(String message, gestionnaireClient expediteur) {
        synchronized (clients) {
            for (gestionnaireClient c : clients) {
                if (c != expediteur) {
                    try {
                        String msgChiffre = AES.crypteAES(message, c.cleAESClient);
                        c.out.writeObject(msgChiffre);
                        c.out.flush();
                    } catch (Exception e) {
                        System.out.println("Erreur broadcast : " + e.getMessage());
                    }
                }
            }
        }
    }

    private void deconnexion() {
        synchronized (clients) {
            clients.remove(this);
        }
        try {
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Client déconnecté : " + client.getInetAddress());
    }
}
