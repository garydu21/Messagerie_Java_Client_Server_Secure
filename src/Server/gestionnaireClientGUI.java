package Server;

import javax.crypto.SecretKey;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import Cryptage.AES;

/**
 * Gestionnaire de client CORRIGÉ pour l'interface graphique
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

    static {
        // Initialiser le salon général
        salons.put("Général", Collections.synchronizedSet(new HashSet<>()));
    }

    public gestionnaireClientGUI(Socket socket) {
        this.client = socket;
        clients.add(this);
    }

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
                salons.get("Général").add(this);
            }

            boolean connexionFerme = false;

            while (!connexionFerme) {
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
                connexionFerme = message.equalsIgnoreCase("bye");
            }

            deconnexion();

        } catch (Exception e) {
            System.out.println("Erreur avec client : " + e.getMessage());
            deconnexion();
        }
    }

    private boolean handleSpecialCommands(String message) {
        try {
            // SET_USERNAME: Définir le pseudo
            if (message.startsWith("SET_USERNAME:")) {
                String newUsername = message.substring(13).trim();
                if (!newUsername.isEmpty()) {
                    System.out.println("Nouveau client: " + newUsername);
                    this.username = newUsername;

                    // ENVOYER LA LISTE DES SALONS AU NOUVEAU CLIENT
                    sendRoomList();

                    // NOTIFIER TOUT LE MONDE DE LA LISTE DES UTILISATEURS
                    broadcastUserList();

                    // Message de bienvenue
                    broadcastToAll("[SYSTÈME] " + username + " vient de se connecter");
                }
                return true;
            }

            // CHANGE_ROOM: Changer de salon
            if (message.startsWith("CHANGE_ROOM:")) {
                String newRoom = message.substring(12).trim();
                changeRoom(newRoom);
                return true;
            }

            // CREATE_ROOM: Créer un nouveau salon
            if (message.startsWith("CREATE_ROOM:")) {
                String roomName = message.substring(12).trim();
                createRoom(roomName);
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
                String salon = message.substring(1, salonEnd);
                String rest = message.substring(salonEnd + 1);

                int usernameEnd = rest.indexOf(":");
                if (usernameEnd > 0) {
                    String extractedUsername = rest.substring(0, usernameEnd).trim();
                    if (!extractedUsername.isEmpty() && !extractedUsername.equals(this.username)) {
                        this.username = extractedUsername;
                        broadcastUserList();
                    }
                    this.currentRoom = salon;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Erreur commande: " + e.getMessage());
            return false;
        }
    }

    /**
     * CHANGER DE SALON
     */
    private void changeRoom(String newRoom) {
        if (newRoom == null || newRoom.trim().isEmpty()) {
            System.err.println("Erreur: nom de salon vide");
            return;
        }

        synchronized (salons) {
            try {
                // Retirer de l'ancien salon
                Set<gestionnaireClientGUI> oldRoomSet = salons.get(currentRoom);
                if (oldRoomSet != null) {
                    oldRoomSet.remove(this);
                }

                // S'assurer que le salon existe
                salons.putIfAbsent(newRoom, Collections.synchronizedSet(new HashSet<>()));

                // Ajouter au nouveau salon
                Set<gestionnaireClientGUI> newRoomSet = salons.get(newRoom);
                if (newRoomSet != null) {
                    newRoomSet.add(this);
                }

                String oldRoom = currentRoom;
                currentRoom = newRoom;

                System.out.println(username + " : " + oldRoom + " → " + newRoom);

                // Confirmer au client
                String confirmation = "[SYSTÈME] Vous êtes dans le salon: " + newRoom;
                sendToClient(confirmation);

            } catch (Exception e) {
                System.err.println("Erreur lors du changement de salon: " + e.getMessage());
                e.printStackTrace();

                // Envoyer message d'erreur au client
                try {
                    sendToClient("[SYSTÈME] Erreur lors du changement de salon");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * CRÉER UN SALON
     */
    private void createRoom(String roomName) {
        synchronized (salons) {
            if (!salons.containsKey(roomName)) {
                salons.put(roomName, Collections.synchronizedSet(new HashSet<>()));
                System.out.println("Nouveau salon créé: " + roomName);

                // NOTIFIER TOUS LES CLIENTS DU NOUVEAU SALON
                broadcastNewRoom(roomName);

                // Envoyer la liste complète des salons à tous
                broadcastRoomListToAll();
            } else {
                try {
                    sendToClient("[SYSTÈME] Le salon " + roomName + " existe déjà");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * MESSAGE PRIVÉ
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
                    e.printStackTrace();
                }
            }
        }

        // Utilisateur non trouvé
        try {
            sendToClient("[SYSTÈME] Utilisateur " + targetUsername + " non trouvé");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * BROADCAST AUX MEMBRES DU MÊME SALON UNIQUEMENT
     */
    private void broadcast(String message, gestionnaireClientGUI expediteur) {
        // Extraire le salon du message
        String salonMessage = expediteur.currentRoom;

        if (message.startsWith("[") && message.contains("]")) {
            try {
                int salonEnd = message.indexOf("]");
                salonMessage = message.substring(1, salonEnd);
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
                        System.out.println("Erreur broadcast à " + c.username + ": " + e.getMessage());
                    }
                }
            }

            System.out.println("→ Message envoyé à " + count + " client(s) dans " + salonMessage);
        }
    }

    /**
     * BROADCAST À TOUS LES CLIENTS
     */
    private void broadcastToAll(String message) {
        for (gestionnaireClientGUI c : clients) {
            try {
                c.sendToClient(message);
            } catch (Exception e) {
                System.out.println("Erreur broadcast global: " + e.getMessage());
            }
        }
    }

    /**
     * NOTIFIER NOUVEAU SALON
     */
    private void broadcastNewRoom(String roomName) {
        for (gestionnaireClientGUI c : clients) {
            try {
                String encrypted = AES.crypteAES("NEW_ROOM:" + roomName, c.cleAESClient);
                c.out.writeObject(encrypted);
                c.out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ENVOYER LISTE DES SALONS À TOUS
     */
    private void broadcastRoomListToAll() {
        String roomList = buildRoomList();
        for (gestionnaireClientGUI c : clients) {
            try {
                String encrypted = AES.crypteAES("ROOM_LIST:" + roomList, c.cleAESClient);
                c.out.writeObject(encrypted);
                c.out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ENVOYER LISTE DES SALONS AU CLIENT
     */
    private void sendRoomList() {
        try {
            String roomList = buildRoomList();
            String encrypted = AES.crypteAES("ROOM_LIST:" + roomList, cleAESClient);
            out.writeObject(encrypted);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * CONSTRUIRE LA LISTE DES SALONS
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
     * BROADCAST LISTE DES UTILISATEURS
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
                e.printStackTrace();
            }
        }
    }

    /**
     * ENVOYER UN MESSAGE À CE CLIENT
     */
    private void sendToClient(String message) throws Exception {
        String msgChiffre = AES.crypteAES(message, cleAESClient);
        out.writeObject(msgChiffre);
        out.flush();
    }

    /**
     * DÉCONNEXION
     */
    private void deconnexion() {
        // Retirer de la liste des clients
        clients.remove(this);

        // Retirer du salon actuel
        synchronized (salons) {
            Set<gestionnaireClientGUI> room = salons.get(currentRoom);
            if (room != null) {
                room.remove(this);
            }
        }

        // Fermer la socket
        try {
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
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
    public String getUsername() {
        return username;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }
}