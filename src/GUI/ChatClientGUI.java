package GUI;

import Cryptage.AES;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Interface graphique du client avec support des salons par onglets.
 * Architecture style TeamSpeak : un onglet par salon avec zone de chat et champ de saisie dedies.
 *
 * Fonctionnalites :
 * - Connexion au serveur avec chiffrement AES-128
 * - Salons multiples avec onglets (style Discord/TeamSpeak)
 * - Messages privées entre utilisateurs
 * - Création dynamique de salons
 * - Double chiffrement : cle client + cle salon
 *
 * Choix Base64 :
 * Les messages chiffres sont en bytes, on utilise Base64 pour les transmettre
 * en tant que String via ObjectOutputStream.
 *
 * @author Chris - Angel
 * @version 2.0
 */
public class ChatClientGUI extends JFrame {

    // Graphiques
    private JTabbedPane roomTabs;
    private Map<String, RoomPanel> roomPanels;
    private JButton connectButton, disconnectButton;
    private JTextField serverField, portField, usernameField;
    private JLabel statusLabel, encryptionLabel;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // Réseau
    private Socket serveur;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private SecretKey cleAESClient;
    private Map<String, SecretKey> roomKeys = new HashMap<>();
    private Thread receptionThread;
    private boolean isConnected = false;

    private static final String DEFAULT_SERVER = "localhost";
    private static final int DEFAULT_PORT = 4444;
    private String username = "User";
    private String currentRoom = "Général";

    private static final Color CONNECTED_COLOR = Color.WHITE;
    private static final Color CONNECTED_BG = new Color(0, 128, 0);
    private static final Color DISCONNECTED_COLOR = Color.WHITE;
    private static final Color DISCONNECTED_BG = new Color(220, 20, 60);
    private static final Color PRIMARY_COLOR = new Color(0, 102, 204);
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private static final Color DANGER_COLOR = new Color(220, 53, 69);
    private static final Color BG_COLOR = new Color(248, 249, 250);

    /**
     * Classe interne représentant un panneau de salon.
     * Chaque salon à sa propre zone de chat et son propre champ de saisie.
     * Permet de séparer visuellement et fonctionnellement chaque salon.
     */
    private class RoomPanel extends JPanel {
        private JTextArea chatArea;
        private JTextField messageField;
        private JButton sendButton;
        private String roomName;

        /**
         * Constructeur du panneau de salon.
         * Création de la zone de chat, champ de saisie et bouton d'envoie.
         *
         * @param roomName Le nom du salon
         */
        public RoomPanel(String roomName) {
            this.roomName = roomName;
            setLayout(new BorderLayout(5, 5));
            setBorder(new EmptyBorder(5, 5, 5, 5));

            // Zone de chat
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            chatArea.setFont(new Font("Consolas", Font.PLAIN, 13));
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(chatArea);
            add(scrollPane, BorderLayout.CENTER);

            // Panel du bas pour écrire
            JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
            bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2, 0, 0, 0, BG_COLOR),
                    BorderFactory.createEmptyBorder(10, 0, 0, 0)
            ));

            // Label du salon
            JLabel roomLabel = new JLabel("💬 " + roomName);
            roomLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            roomLabel.setForeground(PRIMARY_COLOR);

            // Champ de texte
            messageField = new JTextField();
            messageField.setFont(new Font("SansSerif", Font.PLAIN, 14));
            messageField.setEnabled(false);
            messageField.addActionListener(e -> sendMessageInRoom());

            // Bouton envoyer
            sendButton = createButton("Envoyer", Color.BLACK, SUCCESS_COLOR);
            sendButton.setEnabled(false);
            sendButton.addActionListener(e -> sendMessageInRoom());

            // Assemblage
            JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
            inputPanel.add(roomLabel, BorderLayout.NORTH);

            JPanel messagePanel = new JPanel(new BorderLayout(5, 0));
            messagePanel.add(messageField, BorderLayout.CENTER);
            messagePanel.add(sendButton, BorderLayout.EAST);

            inputPanel.add(messagePanel, BorderLayout.CENTER);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);

            add(bottomPanel, BorderLayout.SOUTH);
        }

        /**
         * Envoie un message dans ce salon.
         * Le message est chiffré avec la clé du salon puis envoye au serveur.
         */
        private void sendMessageInRoom() {
            String msg = messageField.getText().trim();
            if (msg.isEmpty() || !isConnected) return;

            try {
                // On regarde si on a la clé du salon
                SecretKey roomKey = roomKeys.get(roomName);
                if (roomKey == null) {
                    appendMessage("[ERREUR] Clé du salon non disponible");
                    return;
                }

                // Chiffrement du message avec la clé du salon
                String msgChiffreAvecCleSalon = AES.crypteAES(msg, roomKey);

                // Formatage du message
                String formattedMsg = "[" + roomName + "]" + username + ": " + msgChiffreAvecCleSalon;

                // Envoie au serveur (chiffré avec la clé client pour la transmission)
                sendEncryptedMessage(formattedMsg);

                // Affichage dans le salon
                appendMessage("[Vous] " + msg);
                messageField.setText("");
            } catch (Exception e) {
                appendMessage("[ERREUR] " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * Ajoute un message dans la zone de chat de ce salon.
         *
         * @param message Le message à afficher
         */
        public void appendMessage(String message) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            chatArea.append("[" + timestamp + "] " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }

        /**
         * Active ou désactive le champ de saisie et le bouton.
         * Utilise lors de la connexion/deconnexion.
         *
         * @param enabled true pour activer, false pour desactiver
         */
        public void setInputEnabled(boolean enabled) {
            messageField.setEnabled(enabled);
            sendButton.setEnabled(enabled);
        }
    }

    /**
     * Constructeur de l'interface graphique.
     * Initialise tous les composants Swing et l'interface utilisateur.
     */
    public ChatClientGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Chat Sécurisé AES-128 - Style TeamSpeak");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(Color.WHITE);

        mainPanel.add(createConnectionPanel(), BorderLayout.NORTH);
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.PAGE_END);

        add(mainPanel);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBackground(BG_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder("Connexion"));

        panel.add(new JLabel("Serveur:"));
        serverField = new JTextField(DEFAULT_SERVER, 12);
        panel.add(serverField);

        panel.add(new JLabel("Port:"));
        portField = new JTextField(String.valueOf(DEFAULT_PORT), 5);
        panel.add(portField);

        panel.add(new JLabel("Pseudo:"));
        usernameField = new JTextField("User" + (int)(Math.random() * 1000), 10);
        panel.add(usernameField);

        connectButton = createButton("Connecter", Color.BLACK, PRIMARY_COLOR);
        connectButton.addActionListener(e -> connectToServer());
        panel.add(connectButton);

        disconnectButton = createButton("Déconnecter", Color.BLACK, DANGER_COLOR);
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> disconnectFromServer());
        panel.add(disconnectButton);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));

        // Tabs pour les salons
        roomTabs = new JTabbedPane();
        roomTabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        roomPanels = new HashMap<>();

        // Créer le salon Général par défaut
        addRoomTab("Général");

        // Listener pour changer de salon quand on change d'onglet
        roomTabs.addChangeListener(e -> {
            int selectedIndex = roomTabs.getSelectedIndex();
            if (selectedIndex >= 0) {
                String roomName = roomTabs.getTitleAt(selectedIndex);
                onRoomTabChanged(roomName);
            }
        });

        panel.add(roomTabs, BorderLayout.CENTER);
        panel.add(createSidePanel(), BorderLayout.EAST);

        return panel;
    }

    private JPanel createSidePanel() {
        JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
        sidePanel.setPreferredSize(new Dimension(200, 0));

        // Bouton pour créer un salon
        JButton createRoomBtn = new JButton("+ Nouveau salon");
        createRoomBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        createRoomBtn.setBackground(PRIMARY_COLOR);
        createRoomBtn.setForeground(Color.BLACK);
        createRoomBtn.addActionListener(e -> promptCreateRoom());

        // Liste des utilisateurs
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(BorderFactory.createTitledBorder("Utilisateurs connectés"));
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        userList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && userList.getSelectedIndex() >= 0) {
                    promptPrivateMessage(userListModel.getElementAt(userList.getSelectedIndex()));
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userList);
        userPanel.add(userScroll, BorderLayout.CENTER);

        sidePanel.add(createRoomBtn, BorderLayout.NORTH);
        sidePanel.add(userPanel, BorderLayout.CENTER);

        return sidePanel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBackground(BG_COLOR);

        statusLabel = new JLabel(" ● Déconnecté ");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setForeground(DISCONNECTED_COLOR);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(DISCONNECTED_BG);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DISCONNECTED_BG.darker(), 2),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        encryptionLabel = new JLabel("🔒 Chiffrement: Inactif");
        encryptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        encryptionLabel.setForeground(Color.DARK_GRAY);

        panel.add(statusLabel);
        panel.add(encryptionLabel);

        return panel;
    }

    /**
     * Ajoute un onglet pour un nouveau salon.
     * Crée le RoomPanel associe et l'ajoute aux onglets.
     *
     * @param roomName Le nom du salon
     */
    private void addRoomTab(String roomName) {
        if (!roomPanels.containsKey(roomName)) {
            RoomPanel roomPanel = new RoomPanel(roomName);
            roomPanels.put(roomName, roomPanel);
            roomTabs.addTab(roomName, roomPanel);
        }
    }

    /**
     * Gere le changement d'onglet de salon.
     * Envoie CHANGE_ROOM au serveur pour recevoir la cle du nouveau salon.
     *
     * @param newRoom Le nom du nouveau salon
     */

    private void onRoomTabChanged(String newRoom) {
        if (!isConnected || newRoom.equals(currentRoom)) return;

        try {
            String oldRoom = currentRoom;
            currentRoom = newRoom;

            // TOUJOURS dire au serveur qu'on change de salon
            sendEncryptedMessage("CHANGE_ROOM:" + currentRoom);

            // Message dans le nouveau salon
            RoomPanel roomPanel = roomPanels.get(newRoom);
            if (roomPanel != null) {
                roomPanel.appendMessage("═══ Vous êtes dans le salon: " + newRoom + " ═══");
            }

        } catch (Exception e) {
            System.err.println("Erreur changement de salon: " + e.getMessage());
        }
    }

    /**
     * Demande à l'utilisateur de créer un nouveau salon.
     * Affiche une boite de dialogue pour saisir le nom du salon.
     * Envoie CREATE_ROOM au serveur et bascule automatiquement sur le nouveau salon.
     */

    private void promptCreateRoom() {
        if (!isConnected) {
            showError("Connectez-vous d'abord !");
            return;
        }

        String roomName = JOptionPane.showInputDialog(this, "Nom du nouveau salon:");
        if (roomName != null && !roomName.trim().isEmpty()) {
            try {
                String trimmedName = roomName.trim();
                sendEncryptedMessage("CREATE_ROOM:" + trimmedName);

                // Ajouter l'onglet localement
                addRoomTab(trimmedName);

                // Basculer vers le nouveau salon automatiquement
                int tabIndex = roomTabs.indexOfTab(trimmedName);
                if (tabIndex >= 0) {
                    roomTabs.setSelectedIndex(tabIndex);
                }

                // Dit au serveur qu'on change de salon
                currentRoom = trimmedName;
                sendEncryptedMessage("CHANGE_ROOM:" + trimmedName);

                getRoomPanel("Général").appendMessage("[SYSTÈME] ✅ Vous avez créé le salon: " + trimmedName);
            } catch (Exception e) {
                showError("Impossible de créer le salon");
            }
        }
    }

    private void promptPrivateMessage(String targetUser) {
        String message = JOptionPane.showInputDialog(this, "Message privé à " + targetUser + ":");
        if (message != null && !message.trim().isEmpty()) {
            try {
                sendEncryptedMessage("PRIVATE_MSG:" + targetUser + ":" + message.trim());
                getRoomPanel(currentRoom).appendMessage("[MP à " + targetUser + "] " + message);
            } catch (Exception e) {
                showError("Erreur MP: " + e.getMessage());
            }
        }
    }

    /**
     * Etablit la connexion au serveur.
     *
     * Processus :
     * 1. Validation du port (1-65535)
     * 2. Création socket avec timeout de 5 secondes
     * 3. Réception de la clé AES personnelle
     * 4. Envoi du nom d'utilisateur
     * 5. Activation de l'interface
     * 6. Démarrage du thread de réception
     */
    private void connectToServer() {
        String server = serverField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                showError("Port doit être entre 1 et 65535 !");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Port invalide !");
            return;
        }

        username = usernameField.getText().trim();
        if (username.isEmpty()) {
            showError("Veuillez entrer un pseudo !");
            return;
        }

        try {
            serveur = new Socket();
            serveur.connect(new InetSocketAddress(server, port), 5000);
            out = new ObjectOutputStream(serveur.getOutputStream());
            out.flush();
            in = new ObjectInputStream(serveur.getInputStream());

            // Réception de la clé AES
            Object kobj = in.readObject();
            byte[] keyBytes = (byte[]) kobj;
            cleAESClient = new SecretKeySpec(keyBytes, "AES");

            isConnected = true;
            setStatusConnected(true);
            encryptionLabel.setText("Chiffrement: AES-128 (" + keyBytes.length * 8 + " bits)");
            encryptionLabel.setForeground(new Color(0, 150, 0));

            // Activer les champs de saisie
            enableRoomInputs(true);

            // Envoyer le username
            sendEncryptedMessage("SET_USERNAME:" + username);

            getRoomPanel("Général").appendMessage("=== Connecté au serveur ===");
            getRoomPanel("Général").appendMessage("Bienvenue " + username + " !");

            // Désactiver/activer les boutons
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            serverField.setEnabled(false);
            portField.setEnabled(false);
            usernameField.setEnabled(false);

            // Démarrer la réception
            startReceiving();

        } catch (Exception e) {
            showError("Erreur de connexion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Déconnecte proprement du serveur.
     * Ferme les flux ObjectInputStream et ObjectOutputStream avant le socket.
     */
    private void disconnectFromServer() {
        try {
            if (isConnected) {
                try {
                    sendEncryptedMessage("bye");
                } catch (Exception e) {
                    System.err.println("Erreur envoi bye: " + e.getMessage());
                }
                isConnected = false;
            }

            if (out != null) {
                try { out.close(); } catch (Exception e) {}
            }
            if (in != null) {
                try { in.close(); } catch (Exception e) {}
            }
            if (serveur != null && !serveur.isClosed()) {
                serveur.close();
            }
            if (receptionThread != null) {
                receptionThread.interrupt();
            }

            setStatusConnected(false);
            encryptionLabel.setText("Chiffrement: Inactif");
            encryptionLabel.setForeground(Color.DARK_GRAY);

            enableRoomInputs(false);

            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            serverField.setEnabled(true);
            portField.setEnabled(true);
            usernameField.setEnabled(true);

            getRoomPanel("Général").appendMessage("=== Déconnecté ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Démarre le thread de reception des messages.
     * Traite les méssages système (clés, listes) et les méssages de salons.
     */
    private void startReceiving() {
        receptionThread = new Thread(() -> {
            try {
                while (isConnected) {
                    String messageChiffre = (String) in.readObject();
                    String message = AES.decrypteAES(messageChiffre, cleAESClient);

                    if (handleSystemMessage(message)) {
                        continue;
                    }

                    final String displayMsg = parseMessage(message);
                    if (displayMsg != null) {
                        SwingUtilities.invokeLater(() -> {
                            RoomPanel roomPanel = getRoomPanel(currentRoom);
                            if (roomPanel != null) {
                                roomPanel.appendMessage(displayMsg);
                                Toolkit.getDefaultToolkit().beep();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                if (isConnected) {
                    SwingUtilities.invokeLater(() -> {
                        getRoomPanel("Général").appendMessage("[SYSTÈME] Connexion perdue");
                        setStatusConnected(false);
                        encryptionLabel.setText("Chiffrement: Inactif");
                        encryptionLabel.setForeground(Color.DARK_GRAY);
                    });
                }
            }
        });
        receptionThread.start();
    }

    private boolean handleSystemMessage(String message) {
        try {
            // Gestion de la clé AES du salon
            if (message.startsWith("ROOM_KEY:")) {
                String keyBase64 = message.substring(9);
                byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
                SecretKey roomKey = new SecretKeySpec(keyBytes, "AES");
                roomKeys.put(currentRoom, roomKey);

                System.out.println("Clé AES reçue pour le salon: " + currentRoom);
                SwingUtilities.invokeLater(() -> {
                    RoomPanel roomPanel = getRoomPanel(currentRoom);
                    if (roomPanel != null) {
                        roomPanel.appendMessage("[SYSTÈME] Clé de chiffrement reçue");
                        roomPanel.setInputEnabled(true); // Activer le champ !
                    }
                });
                return true;
            }

            if (message.startsWith("USER_LIST:")) {
                String users = message.substring(10);
                SwingUtilities.invokeLater(() -> updateUserList(users));
                return true;
            }

            if (message.startsWith("NEW_ROOM:")) {
                String roomName = message.substring(9);
                SwingUtilities.invokeLater(() -> {
                    addRoomTab(roomName);
                    getRoomPanel("Général").appendMessage("[SYSTÈME] Nouveau salon disponible: " + roomName + " (cliquez sur l'onglet pour y accéder)");
                });
                return true;
            }

            if (message.startsWith("ROOM_LIST:")) {
                String rooms = message.substring(10);
                SwingUtilities.invokeLater(() -> updateRoomList(rooms));
                return true;
            }

            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String parseMessage(String message) {
        if (message.startsWith("[SYSTÈME]") || message.startsWith("[MP")) {
            return message;
        }

        if (message.startsWith("[") && message.contains("]") && message.contains(":")) {
            try {
                int salonEnd = message.indexOf("]");
                String salon = message.substring(1, salonEnd);
                String rest = message.substring(salonEnd + 1);

                int usernameEnd = rest.indexOf(":");
                String senderUsername = rest.substring(0, usernameEnd).trim();
                String encryptedContent = rest.substring(usernameEnd + 1).trim();

                if (!salon.equals(currentRoom)) {
                    System.out.println("Message ignoré - Salon: " + salon + " (actuel: " + currentRoom + ")");
                    return null;
                }

                // Déchiffre le contenu à l'aide de la clé du salon
                SecretKey roomKey = roomKeys.get(salon);
                if (roomKey != null) {
                    try {
                        String decryptedContent = AES.decrypteAES(encryptedContent, roomKey);
                        return "[" + senderUsername + "] " + decryptedContent;
                    } catch (Exception e) {
                        return "[" + senderUsername + "] [Message chiffré - clé invalide]";
                    }
                } else {
                    return "[" + senderUsername + "] [Message chiffré - clé manquante]";
                }
            } catch (Exception e) {
                return message;
            }
        }

        return message;
    }

    /**
     * Envoie un message chiffré au serveur.
     * Le message est chiffré avec la clé personnelle du client.
     *
     * @param message Le message en clair
     * @throws Exception Si le chiffrement ou l'envoi echoue
     */
    private void sendEncryptedMessage(String message) throws Exception {
        String msgChiffre = AES.crypteAES(message, cleAESClient);
        out.writeObject(msgChiffre);
        out.flush();
    }

    private void updateUserList(String userListString) {
        userListModel.clear();
        if (!userListString.isEmpty()) {
            String[] users = userListString.split(",");
            for (String user : users) {
                String trimmed = user.trim();
                if (!trimmed.isEmpty() && !trimmed.equals(username)) {
                    userListModel.addElement(trimmed);
                }
            }
        }
    }

    private void updateRoomList(String roomListString) {
        if (!roomListString.isEmpty()) {
            String[] rooms = roomListString.split(",");
            for (String room : rooms) {
                String trimmed = room.trim();
                if (!trimmed.isEmpty()) {
                    addRoomTab(trimmed);
                }
            }
        }
    }

    private RoomPanel getRoomPanel(String roomName) {
        return roomPanels.get(roomName);
    }

    private void enableRoomInputs(boolean enabled) {
        for (RoomPanel panel : roomPanels.values()) {
            panel.setInputEnabled(enabled);
        }
    }

    private void setStatusConnected(boolean connected) {
        if (connected) {
            statusLabel.setText(" ● Connecté ");
            statusLabel.setForeground(CONNECTED_COLOR);
            statusLabel.setBackground(CONNECTED_BG);
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CONNECTED_BG.darker(), 2),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)
            ));
        } else {
            statusLabel.setText(" ● Déconnecté ");
            statusLabel.setForeground(DISCONNECTED_COLOR);
            statusLabel.setBackground(DISCONNECTED_BG);
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DISCONNECTED_BG.darker(), 2),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)
            ));
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Erreur", JOptionPane.ERROR_MESSAGE);
    }

    private JButton createButton(String text, Color textColor, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setForeground(textColor);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bgColor.darker(), 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(bgColor.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bgColor);
            }
        });

        return btn;
    }

    /**
     * Lanceur de l'interface graphique.
     *
     * @param args Par defaut
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            ChatClientGUI gui = new ChatClientGUI();
            gui.setVisible(true);
        });
    }
}