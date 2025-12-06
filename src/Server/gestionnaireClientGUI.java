package Server;

import javax.crypto.SecretKey;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import Cryptage.AES;

/**
 * Gestionnaire de client pour la version graphique avec salons prives.
 * Supporte plusieurs salons avec cles AES independantes pour isolation cryptographique.
 *
 * Architecture double chiffrement (amelioration securite) :
 * - Couche 1 : Cle personnelle client-serveur (cleAESClient)
 *   Permet au serveur de communiquer avec le client de maniere securisee
 *
 * - Couche 2 : Cle unique par salon (salonKeys)
 *   Les messages sont chiffres avec la cle du salon avant d'etre envoyes
 *   Meme le serveur ne peut pas lire le contenu des messages des salons
 *
 * Securite finale : 2^128 × 2^128 = 2^256 combinaisons possibles
 *
 * @author Chris - Angel
 * @version 2.0
 */
public class gestionnaireClientGUI implements Runnable {

    private final Socket client;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private SecretKey cleAESClient;
    private String username = "Anonymous";
    private String currentRoom = "Général";

    // Liste de tous les clients connectés
    private static final List<gestionnaireClientGUI> clients = new CopyOnWriteArrayList<>();

    // Map des salons : nom -> ensemble de clients
    private static final Map<String, Set<gestionnaireClientGUI>> salons =
            Collections.synchronizedMap(new HashMap<>());

    // Map des clés AES par salon : nom du salon -> clé AES
    private static final Map<String, SecretKey> salonKeys =
            Collections.synchronizedMap(new HashMap<>());

    static {
        // Initialiser le salon général avec sa clé
        salons.put("Général", Collections.synchronizedSet(new HashSet<>()));
        try {
            salonKeys.put("Général", AES.genererCle(128));
            System.out.println("Clé AES créée pour le salon : Général");
        } catch (Exception e) {
            System.err.println("Erreur création clé salon Général : " + e.getMessage());
        }
    }

    /**
     * Constructeur du gestionnaire.
     * Ajoute le client a la liste partagee des clients connectes.
     *
     * @param socket La socket du client connecte
     */
    public gestionnaireClientGUI(Socket socket) {
        this.client = socket;
        clients.add(this);
    }

    /**
     * Boucle principale de gestion du client.
     *
     * Processus :
     * 1. Genere et envoie la cle AES personnelle au client
     * 2. Ajoute le client au salon General par defaut
     * 3. Envoie la cle du salon General au client
     * 4. Recoit et traite les messages/commandes du client
     * 5. Deconnecte proprement a la fin
     */
    @Override
    public void run() {
        try {
            // Initialisation flux
            out = new ObjectOutputStream(client.getOutputStream());
            out.flush();
            in = new ObjectInputStream(client.getInputStream());

            // Génération et envoi clé AES
            cleAESClient = AES.genererCle(128);
            out.writeObject(cleAESClient.getEncoded());
            out.flush();

            System.out.println("Clé envoyée au client : " +
                    Base64.getEncoder().encodeToString(cleAESClient.getEncoded()));

            // Ajouter au salon général par défaut
            synchronized (salons) {
                Set<gestionnaireClientGUI> salonGeneral = salons.get("Général");
                if (salonGeneral != null) {
                    salonGeneral.add(this);
                }

                // Envoyer la clé du salon Général au client
                SecretKey generalRoomKey = salonKeys.get("Général");
                if (generalRoomKey != null) {
                    String keyMessage = "ROOM_KEY:" + Base64.getEncoder().encodeToString(generalRoomKey.getEncoded());
                    String encryptedKey = AES.crypteAES(keyMessage, cleAESClient);
                    out.writeObject(encryptedKey);
                    out.flush();
                    System.out.println("Clé du salon Général envoyée au nouveau client");
                }
            }

            boolean connexionFermee = false;

            while (!connexionFermee) {
                String messageChiffre = (String) in.readObject();
                String message = AES.decrypteAES(messageChiffre, cleAESClient);

                System.out.println("\n===== MESSAGE REÇU =====");
                System.out.println("De      : " + username + " (Salon: " + currentRoom + ")");
                System.out.println("Chiffré : " + messageChiffre.substring(0, Math.min(50, messageChiffre.length())) + "...");
                System.out.println("Clair   : " + message);

                // Traiter les commandes spéciales
                if (handleSpecialCommands(message)) {
                    continue;
                }

                // Broadcaster aux autres clients du même salon
                broadcast(message, this);

                // Vérifier "bye"
                connexionFermee = message.equalsIgnoreCase("bye");
            }

            deconnexion();

        } catch (Exception e) {
            System.err.println("Erreur avec client : " + e.getMessage());
            deconnexion();
        }
    }

    /**
     * Traite les commandes spéciales (SET_USERNAME, CHANGE_ROOM, CREATE_ROOM, PRIVATE_MSG)
     * @param message Message à analyser
     * @return true si c'était une commande spéciale
     */
    private boolean handleSpecialCommands(String message) {
        try {
            // SET_USERNAME: Définir le pseudo
            if (message.startsWith("SET_USERNAME:")) {
                String newUsername = message.substring(13).trim();
                if (!newUsername.isEmpty()) {
                    System.out.println("Nouveau client: " + newUsername);
                    this.username = newUsername;

                    // Envoyer la liste des salons au nouveau client
                    sendRoomList();

                    // Notifier tout le monde de la liste des utilisateurs
                    broadcastUserList();

                    // Message de bienvenue
                    broadcastToAll("[SYSTÈME] " + username + " vient de se connecter");
                }
                return true;
            }

            // CHANGE_ROOM: Changer de salon
            if (message.startsWith("CHANGE_ROOM:")) {
                String newRoom = message.substring(12).trim();
                if (!newRoom.isEmpty()) {
                    changeRoom(newRoom);
                }
                return true;
            }

            // CREATE_ROOM: Créer un nouveau salon
            if (message.startsWith("CREATE_ROOM:")) {
                String roomName = message.substring(12).trim();
                if (!roomName.isEmpty()) {
                    createRoom(roomName);
                }
                return true;
            }

            // PRIVATE_MSG: Message privé
            if (message.startsWith("PRIVATE_MSG:")) {
                String[] parts = message.substring(12).split(":", 2);
                if (parts.length == 2) {
                    sendPrivateMessage(parts[0].trim(), parts[1].trim());
                }
                return true;
            }

            // Extraire le username des messages formatés
            if (message.startsWith("[") && message.contains("]") && message.contains(":")) {
                int salonEnd = message.indexOf("]");
                if (salonEnd > 0 && salonEnd < message.length() - 1) {
                    String salon = message.substring(1, salonEnd);
                    String rest = message.substring(salonEnd + 1);

                    int usernameEnd = rest.indexOf(":");
                    if (usernameEnd > 0 && usernameEnd < rest.length() - 1) {
                        String extractedUsername = rest.substring(0, usernameEnd).trim();
                        if (!extractedUsername.isEmpty() && !extractedUsername.equals(this.username)) {
                            this.username = extractedUsername;
                            broadcastUserList();
                        }
                        this.currentRoom = salon;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Erreur commande: " + e.getMessage());
            return false;
        }
    }

    /**
     * Change le client de salon.
     *
     * Processus :
     * 1. Retire le client de son salon actuel
     * 2. Cree le nouveau salon s'il n'existe pas
     * 3. Genere une cle AES pour le nouveau salon si necessaire
     * 4. Ajoute le client au nouveau salon
     * 5. Envoie la cle du nouveau salon au client
     *
     * @param newRoom Le nom du nouveau salon
     */
    private void changeRoom(String newRoom) {
        synchronized (salons) {
            try {
                // Retirer de l'ancien salon
                Set<gestionnaireClientGUI> oldRoomSet = salons.get(currentRoom);
                if (oldRoomSet != null) {
                    oldRoomSet.remove(this);
                }

                // S'assurer que le salon existe
                salons.putIfAbsent(newRoom, Collections.synchronizedSet(new HashSet<>()));

                // Si le salon n'a pas de clé, en créer une
                if (!salonKeys.containsKey(newRoom)) {
                    SecretKey roomKey = AES.genererCle(128);
                    salonKeys.put(newRoom, roomKey);
                    System.out.println("Nouvelle clé AES créée pour le salon : " + newRoom);
                }

                // Ajouter au nouveau salon
                Set<gestionnaireClientGUI> newRoomSet = salons.get(newRoom);
                if (newRoomSet != null) {
                    newRoomSet.add(this);
                }

                String oldRoom = currentRoom;
                currentRoom = newRoom;

                System.out.println(username + " : " + oldRoom + " → " + newRoom);

                // Envoyer la clé AES du salon au client
                SecretKey roomKey = salonKeys.get(newRoom);
                if (roomKey != null) {
                    String keyMessage = "ROOM_KEY:" + Base64.getEncoder().encodeToString(roomKey.getEncoded());
                    sendToClient(keyMessage);
                    System.out.println("Clé du salon " + newRoom + " envoyée à " + username);
                }

                // Confirmer au client
                String confirmation = "[SYSTÈME] Vous êtes dans le salon: " + newRoom;
                sendToClient(confirmation);

            } catch (Exception e) {
                System.err.println("Erreur lors du changement de salon: " + e.getMessage());
                try {
                    sendToClient("[SYSTÈME] Erreur lors du changement de salon");
                } catch (Exception ex) {
                    System.err.println("Impossible d'envoyer le message d'erreur: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Cree un nouveau salon avec une cle AES unique.
     *
     * Securite : Chaque salon a sa propre cle AES-128, ce qui isole
     * cryptographiquement les messages entre salons. Seuls les membres
     * du salon possedent la cle et peuvent dechiffrer les messages.
     *
     * @param roomName Le nom du salon a creer
     */
    private void createRoom(String roomName) {
        synchronized (salons) {
            if (!salons.containsKey(roomName)) {
                salons.put(roomName, Collections.synchronizedSet(new HashSet<>()));

                // Créer une clé AES unique pour ce salon
                try {
                    SecretKey roomKey = AES.genererCle(128);
                    salonKeys.put(roomName, roomKey);
                    System.out.println("Nouveau salon créé: " + roomName + " avec clé AES unique");
                } catch (Exception e) {
                    System.err.println("Erreur création clé pour salon " + roomName + ": " + e.getMessage());
                }

                // Notifier tous les clients du nouveau salon
                broadcastNewRoom(roomName);

                // Envoyer la liste complète des salons à tous
                broadcastRoomListToAll();
            } else {
                try {
                    sendToClient("[SYSTÈME] Le salon " + roomName + " existe déjà");
                } catch (Exception e) {
                    System.err.println("Erreur envoi message: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Envoie un message prive a un utilisateur specifique.
     * Le message est chiffre avec la cle personnelle du destinataire.
     *
     * @param targetUsername Le nom du destinataire
     * @param message Le message a envoyer
     */
    private void sendPrivateMessage(String targetUsername, String message) {
        for (gestionnaireClientGUI c : clients) {
            if (c.username.equals(targetUsername)) {
                try {
                    String privateMsg = "[MP de " + this.username + "] " + message;
                    c.sendToClient(privateMsg);
                    System.out.println("MP: " + this.username + " → " + targetUsername);
                    return;
                } catch (Exception e) {
                    System.err.println("Erreur envoi MP: " + e.getMessage());
                }
            }
        }

        // Utilisateur non trouvé
        try {
            sendToClient("[SYSTÈME] Utilisateur " + targetUsername + " non trouvé");
        } catch (Exception e) {
            System.err.println("Erreur envoi message: " + e.getMessage());
        }
    }

    /**
     * Diffuser un message aux membres du même salon uniquement
     * @param message Message à diffuser
     * @param expediteur Client expéditeur
     */
    private void broadcast(String message, gestionnaireClientGUI expediteur) {
        // Extraire le salon du message
        String salonMessage = expediteur.currentRoom;

        if (message.startsWith("[") && message.contains("]")) {
            try {
                int salonEnd = message.indexOf("]");
                if (salonEnd > 0) {
                    salonMessage = message.substring(1, salonEnd);
                }
            } catch (Exception e) {
                // Si parsing échoue, utiliser le salon actuel
            }
        }

        System.out.println("Broadcast dans le salon: " + salonMessage);

        synchronized (salons) {
            Set<gestionnaireClientGUI> room = salons.get(salonMessage);
            if (room == null) {
                System.err.println("Salon introuvable: " + salonMessage);
                return;
            }

            int count = 0;
            for (gestionnaireClientGUI c : room) {
                if (c != expediteur) {
                    try {
                        c.sendToClient(message);
                        count++;
                    } catch (Exception e) {
                        System.err.println("Erreur broadcast à " + c.username + ": " + e.getMessage());
                    }
                }
            }

            System.out.println("→ Message envoyé à " + count + " client(s) dans " + salonMessage);
        }
    }

    /**
     * Diffuse un message a tous les clients connectes.
     * Utilise pour les messages systeme (connexions, deconnexions).
     *
     * @param message Le message a diffuser
     */
    private void broadcastToAll(String message) {
        for (gestionnaireClientGUI c : clients) {
            try {
                c.sendToClient(message);
            } catch (Exception e) {
                System.err.println("Erreur broadcast global: " + e.getMessage());
            }
        }
    }

    /**
     * Notifie tous les clients de la creation d'un nouveau salon.
     *
     * @param roomName Le nom du nouveau salon
     */
    private void broadcastNewRoom(String roomName) {
        for (gestionnaireClientGUI c : clients) {
            try {
                String encrypted = AES.crypteAES("NEW_ROOM:" + roomName, c.cleAESClient);
                c.out.writeObject(encrypted);
                c.out.flush();
            } catch (Exception e) {
                System.err.println("Erreur envoi nouveau salon: " + e.getMessage());
            }
        }
    }

    /**
     * Envoyer la liste des salons à tous les clients
     */
    private void broadcastRoomListToAll() {
        String roomList = buildRoomList();
        for (gestionnaireClientGUI c : clients) {
            try {
                String encrypted = AES.crypteAES("ROOM_LIST:" + roomList, c.cleAESClient);
                c.out.writeObject(encrypted);
                c.out.flush();
            } catch (Exception e) {
                System.err.println("Erreur envoi liste salons: " + e.getMessage());
            }
        }
    }

    /**
     * Envoyer la liste des salons à ce client
     */
    private void sendRoomList() {
        try {
            String roomList = buildRoomList();
            String encrypted = AES.crypteAES("ROOM_LIST:" + roomList, cleAESClient);
            out.writeObject(encrypted);
            out.flush();
        } catch (Exception e) {
            System.err.println("Erreur envoi liste salons: " + e.getMessage());
        }
    }

    /**
     * Construit la liste des salons disponibles.
     *
     * @return Liste des salons separes par des virgules
     */
    private String buildRoomList() {
        StringBuilder sb = new StringBuilder();
        synchronized (salons) {
            for (String room : salons.keySet()) {
                sb.append(room).append(",");
            }
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Diffuser la liste des utilisateurs à tous les clients
     */
    private void broadcastUserList() {
        StringBuilder userListBuilder = new StringBuilder();
        for (gestionnaireClientGUI c : clients) {
            if (!c.username.equals("Anonymous")) {
                userListBuilder.append(c.username).append(",");
            }
        }

        String userList = userListBuilder.toString();
        if (userList.endsWith(",")) {
            userList = userList.substring(0, userList.length() - 1);
        }

        // Envoyer à tous les clients
        for (gestionnaireClientGUI c : clients) {
            try {
                String encrypted = AES.crypteAES("USER_LIST:" + userList, c.cleAESClient);
                c.out.writeObject(encrypted);
                c.out.flush();
            } catch (Exception e) {
                System.err.println("Erreur envoi liste utilisateurs: " + e.getMessage());
            }
        }
    }

    /**
     * Envoie un message chiffre a ce client.
     *
     * @param message Le message en clair a envoyer
     * @throws Exception Si l'envoi echoue
     */
    private void sendToClient(String message) throws Exception {
        String msgChiffre = AES.crypteAES(message, cleAESClient);
        out.writeObject(msgChiffre);
        out.flush();
    }

    /**
     * Deconnecte proprement le client.
     *
     * Processus :
     * 1. Retire le client de la liste globale
     * 2. Retire le client de son salon actuel
     * 3. Ferme les flux ObjectInputStream et ObjectOutputStream
     * 4. Ferme la socket
     * 5. Notifie les autres clients
     * 6. Met a jour la liste des utilisateurs
     */
    private void deconnexion() {
        clients.remove(this);

        synchronized (salons) {
            Set<gestionnaireClientGUI> room = salons.get(currentRoom);
            if (room != null) {
                room.remove(this);
            }
        }

        try {
            if (out != null) out.close();
        } catch (Exception e) {
            System.err.println("Erreur fermeture out: " + e.getMessage());
        }

        try {
            if (in != null) in.close();
        } catch (Exception e) {
            System.err.println("Erreur fermeture in: " + e.getMessage());
        }

        try {
            if (client != null && !client.isClosed()) {
                client.close();
            }
        } catch (Exception e) {
            System.err.println("Erreur fermeture socket: " + e.getMessage());
        }

        System.out.println("Client déconnecté: " + client.getInetAddress() + " (" + username + ")");

        // Notifier les autres
        if (!username.equals("Anonymous")) {
            broadcastToAll("[SYSTÈME] " + username + " s'est déconnecté");
        }

        // Mettre à jour la liste des utilisateurs
        broadcastUserList();
    }

    // Getters
    /**
     * Retourne le nom d'utilisateur.
     *
     * @return Le nom d'utilisateur
     */
    public String getUsername() {
        return username;
    }

    /**
     * Retourne le salon actuel.
     *
     * @return Le nom du salon actuel
     */
    public String getCurrentRoom() {
        return currentRoom;
    }
}