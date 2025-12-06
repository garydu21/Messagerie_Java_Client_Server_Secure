package Server;

import javax.crypto.SecretKey;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import Cryptage.AES;

/**
 * Gestionnaire de client pour la version console.
 * Chaque client connecte a son propre thread et sa propre cle AES.
 * Les messages sont diffuses a tous les autres clients (broadcast).
 *
 * Choix AES-128 :
 * - Standard industriel securise (2^128 combinaisons)
 * - Plus rapide que AES-256 pour un niveau de securite suffisant
 *
 * @author Chris - Angel
 * @version 1.0
 */
public class gestionnaireClient implements Runnable {

    private final Socket client;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private SecretKey cleAESClient;

    /**
     * Liste partagee de tous les clients connectes.
     * CopyOnWriteArrayList pour securite.
     */
    private static final List<gestionnaireClient> clients = new CopyOnWriteArrayList<>();

    /**
     * Constructeur du gestionnaire.
     * Ajoute le client a la liste partagee.
     *
     * @param socket La socket du client connecte
     */
    public gestionnaireClient(Socket socket) {
        this.client = socket;
        clients.add(this);
    }

    /**
     * Boucle principale de gestion du client.
     * 1. Genere et envoie une cle AES unique au client
     * 2. Recoit les messages chiffres du client
     * 3. Dechiffre et broadcast aux autres clients
     * 4. Termine sur reception de "bye"
     */
    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(client.getOutputStream());
            out.flush();
            in = new ObjectInputStream(client.getInputStream());

            // Generation cle AES-128 unique pour ce client
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

    /**
     * Diffuse un message a tous les autres clients.
     * Chaque client recoit le message chiffre avec sa propre cle.
     * L'expediteur ne recoit pas son propre message.
     *
     * @param message Le message en clair a diffuser
     * @param expediteur Le client qui a envoye le message
     */
    private void broadcast(String message, gestionnaireClient expediteur) {
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

    /**
     * Deconnecte proprement le client.
     * Retire le client de la liste et ferme la socket.
     */
    private void deconnexion() {
        clients.remove(this);
        try {
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Client déconnecté : " + client.getInetAddress());
    }
}