package GUI;

import Cryptage.AES;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Interface graphique du client
 */
public class ChatClientGUI extends JFrame {

    // graphiques
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton, connectButton, disconnectButton;
    private JTextField serverField, portField, usernameField;
    private JLabel statusLabel, encryptionLabel, currentRoomLabel;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JComboBox<String> roomSelector;
    private DefaultComboBoxModel<String> roomModel;

    // Réseau
    private Socket serveur;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private SecretKey cleAESClient;
    private Thread receptionThread;
    private boolean isConnected = false;

    private static final String DEFAULT_SERVER = "localhost";
    private static final int DEFAULT_PORT = 4444;
    private String username = "User";
    private String currentRoom = "Général";

    // TODO: couleurs plus vive
    private static final Color CONNECTED_COLOR = Color.WHITE;
    private static final Color CONNECTED_BG = new Color(0, 128, 0);
    private static final Color DISCONNECTED_COLOR = Color.WHITE;
    private static final Color DISCONNECTED_BG = new Color(220, 20, 60);
    private static final Color PRIMARY_COLOR = new Color(0, 102, 204);
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private static final Color DANGER_COLOR = new Color(220, 53, 69);
    private static final Color BG_COLOR = new Color(248, 249, 250);

    public ChatClientGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Chat Sécurisé AES-128");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(Color.WHITE);

        mainPanel.add(createConnectionPanel(), BorderLayout.NORTH);
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
        mainPanel.add(createMessagePanel(), BorderLayout.SOUTH);
        mainPanel.add(createStatusBar(), BorderLayout.PAGE_END);

        add(mainPanel);
        setControlsEnabled(false);
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

        connectButton = createButton("Connecter", Color.WHITE, PRIMARY_COLOR);
        connectButton.addActionListener(e -> connectToServer());
        panel.add(connectButton);

        disconnectButton = createButton("Déconnecter", Color.WHITE, DANGER_COLOR);
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> disconnectFromServer());
        panel.add(disconnectButton);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Messages (AES-128)"));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        panel.add(chatPanel, BorderLayout.CENTER);
        panel.add(createSidePanel(), BorderLayout.EAST);

        return panel;
    }

    private JPanel createSidePanel() {
        JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
        sidePanel.setPreferredSize(new Dimension(200, 0));

        JPanel roomPanel = new JPanel(new BorderLayout(5, 5));
        roomPanel.setBorder(BorderFactory.createTitledBorder("Salon"));
        roomModel = new DefaultComboBoxModel<>();
        roomModel.addElement("Général");
        roomSelector = new JComboBox<>(roomModel);
        roomSelector.addActionListener(e -> changeRoom());
        roomPanel.add(roomSelector, BorderLayout.NORTH);

        JButton createRoomBtn = new JButton("+ Nouveau salon");
        createRoomBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        createRoomBtn.addActionListener(e -> promptCreateRoom());
        roomPanel.add(createRoomBtn, BorderLayout.SOUTH);

        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(BorderFactory.createTitledBorder("Utilisateurs"));
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

        sidePanel.add(roomPanel, BorderLayout.NORTH);
        sidePanel.add(userPanel, BorderLayout.CENTER);

        return sidePanel;
    }

    private JPanel createMessagePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Envoyer un message"));

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        currentRoomLabel = new JLabel("📍 " + currentRoom);
        currentRoomLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        currentRoomLabel.setForeground(PRIMARY_COLOR);
        currentRoomLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 10));

        messageField = new JTextField();
        messageField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageField.addActionListener(e -> sendMessage());

        inputPanel.add(currentRoomLabel, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);

        sendButton = createButton("Envoyer", Color.WHITE, SUCCESS_COLOR);
        sendButton.addActionListener(e -> sendMessage());

        panel.add(inputPanel, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        return panel;
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

    private void connectToServer() {
        String server = serverField.getText().trim();
        int port;

        try {
            port = Integer.parseInt(portField.getText().trim());
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
            serveur = new Socket(server, port);

            out = new ObjectOutputStream(serveur.getOutputStream());
            out.flush();
            in = new ObjectInputStream(serveur.getInputStream());

            Object kobj = in.readObject();
            byte[] keyBytes = (byte[]) kobj;
            cleAESClient = new SecretKeySpec(keyBytes, "AES");

            isConnected = true;
            sendEncryptedMessage("SET_USERNAME:" + username);

            // Mettre à jour le statut - CONNECTÉ
            setStatusConnected(true);

            encryptionLabel.setText("🔒 AES-128: " + Base64.getEncoder().encodeToString(keyBytes).substring(0, 12) + "...");
            encryptionLabel.setForeground(CONNECTED_BG);

            appendToChat("[SYSTÈME] Connecté au serveur " + server + ":" + port, Color.BLUE);
            appendToChat("[SYSTÈME] Chiffrement AES-128 activé", Color.BLUE);

            startReceptionThread();

            setControlsEnabled(true);
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            serverField.setEnabled(false);
            portField.setEnabled(false);
            usernameField.setEnabled(false);

        } catch (Exception e) {
            showError("Erreur de connexion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startReceptionThread() {
        receptionThread = new Thread(() -> {
            try {
                while (isConnected && !Thread.interrupted()) {
                    String messageChiffre = (String) in.readObject();
                    String message = AES.decrypteAES(messageChiffre, cleAESClient);

                    if (handleSystemMessage(message)) {
                        continue;
                    }

                    final String displayMsg = parseMessage(message);
                    if (displayMsg != null) {
                        SwingUtilities.invokeLater(() -> {
                            appendToChat(displayMsg, Color.BLACK);
                            Toolkit.getDefaultToolkit().beep();
                        });
                    }
                }
            } catch (Exception e) {
                if (isConnected) {
                    SwingUtilities.invokeLater(() -> {
                        appendToChat("[SYSTÈME] Connexion perdue", Color.RED);
                        setStatusConnected(false);
                        encryptionLabel.setText("🔒 Chiffrement: Inactif");
                        encryptionLabel.setForeground(Color.DARK_GRAY);
                    });
                }
            }
        });
        receptionThread.start();
    }

    private boolean handleSystemMessage(String message) {
        try {
            if (message.startsWith("USER_LIST:")) {
                String users = message.substring(10);
                SwingUtilities.invokeLater(() -> updateUserList(users));
                return true;
            }

            if (message.startsWith("NEW_ROOM:")) {
                String roomName = message.substring(9);
                SwingUtilities.invokeLater(() -> {
                    if (!roomExists(roomName)) {
                        roomModel.addElement(roomName);
                        appendToChat("[SYSTÈME] Nouveau salon: " + roomName, Color.BLUE);
                    }
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

    private void sendMessage() {
        String msg = messageField.getText().trim();

        if (msg.isEmpty()) return;
        if (!isConnected) {
            showError("Vous n'êtes pas connecté !");
            return;
        }

        try {
            String formattedMsg = "[" + currentRoom + "]" + username + ": " + msg;
            sendEncryptedMessage(formattedMsg);
            appendToChat("[Vous ➤ " + currentRoom + "] " + msg, new Color(0, 120, 0));
            messageField.setText("");
        } catch (Exception e) {
            showError("Erreur d'envoi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendEncryptedMessage(String message) throws Exception {
        String msgChiffre = AES.crypteAES(message, cleAESClient);
        out.writeObject(msgChiffre);
        out.flush();
    }

    private void disconnectFromServer() {
        try {
            if (isConnected) {
                sendEncryptedMessage("bye");

                if (out != null) out.close();
                if (in != null) in.close();
                if (serveur != null) serveur.close();
                if (receptionThread != null) receptionThread.interrupt();

                isConnected = false;
                setStatusConnected(false);
                encryptionLabel.setText("🔒 Chiffrement: Inactif");
                encryptionLabel.setForeground(Color.DARK_GRAY);
                appendToChat("[SYSTÈME] Déconnecté du serveur", Color.RED);

                setControlsEnabled(false);
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                serverField.setEnabled(true);
                portField.setEnabled(true);
                usernameField.setEnabled(true);
                userListModel.clear();
            }
        } catch (Exception e) {
            showError("Erreur déconnexion: " + e.getMessage());
        }
    }

    private void changeRoom() {
        if (!isConnected) return;

        String newRoom = (String) roomSelector.getSelectedItem();
        if (newRoom == null || newRoom.equals(currentRoom)) {
            return;
        }

        try {
            String oldRoom = currentRoom;
            currentRoom = newRoom;

            if (currentRoomLabel != null) {
                currentRoomLabel.setText("📍 " + currentRoom);
            }

            sendEncryptedMessage("CHANGE_ROOM:" + currentRoom);

            chatArea.append("\n");
            chatArea.append("═══════════════════════════════════════════════════\n");
            chatArea.append("    CHANGEMENT DE SALON : " + oldRoom + " → " + currentRoom + "\n");
            chatArea.append("═══════════════════════════════════════════════════\n");
            chatArea.append("\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } catch (Exception e) {
            appendToChat("[ERREUR] Impossible de changer de salon: " + e.getMessage(), Color.RED);
            e.printStackTrace();
        }
    }

    private void promptCreateRoom() {
        if (!isConnected) {
            showError("Connectez-vous d'abord !");
            return;
        }

        String roomName = JOptionPane.showInputDialog(this, "Nom du nouveau salon:");
        if (roomName != null && !roomName.trim().isEmpty()) {
            try {
                sendEncryptedMessage("CREATE_ROOM:" + roomName.trim());
                appendToChat("[SYSTÈME] Demande de création: " + roomName, Color.BLUE);
            } catch (Exception e) {
                appendToChat("[ERREUR] Impossible de créer le salon", Color.RED);
            }
        }
    }

    private void promptPrivateMessage(String targetUser) {
        String message = JOptionPane.showInputDialog(this, "Message privé à " + targetUser + ":");
        if (message != null && !message.trim().isEmpty()) {
            try {
                sendEncryptedMessage("PRIVATE_MSG:" + targetUser + ":" + message.trim());
                appendToChat("[MP à " + targetUser + "] " + message, new Color(138, 43, 226));
            } catch (Exception e) {
                showError("Erreur MP: " + e.getMessage());
            }
        }
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
        String selected = (String) roomSelector.getSelectedItem();
        roomModel.removeAllElements();

        if (!roomListString.isEmpty()) {
            String[] rooms = roomListString.split(",");
            for (String room : rooms) {
                String trimmed = room.trim();
                if (!trimmed.isEmpty()) {
                    roomModel.addElement(trimmed);
                }
            }
        }

        if (selected != null && roomExists(selected)) {
            roomSelector.setSelectedItem(selected);
        }
    }

    private boolean roomExists(String roomName) {
        for (int i = 0; i < roomModel.getSize(); i++) {
            if (roomModel.getElementAt(i).equals(roomName)) {
                return true;
            }
        }
        return false;
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
                String content = rest.substring(usernameEnd + 1).trim();

                if (!salon.equals(currentRoom)) {
                    System.out.println("Message ignoré - Salon: " + salon + " (actuel: " + currentRoom + ")");
                    return null;
                }

                return "[" + senderUsername + " ➤ " + salon + "] " + content;
            } catch (Exception e) {
                return message;
            }
        }

        return message;
    }

    /**
     * MISE À JOUR DU STATUT CONNECTÉ/DÉCONNECTÉ
     */
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

    private void appendToChat(String message, Color color) {
        if (message == null) return;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        chatArea.append("[" + timestamp + "] " + message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void setControlsEnabled(boolean enabled) {
        messageField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        roomSelector.setEnabled(enabled);
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