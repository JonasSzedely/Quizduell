package org.Codes.Alte_Programme;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class QuizClientMitPunkte {
    private Dimension bildschirmAufloesung = Toolkit.getDefaultToolkit().getScreenSize();

    private JLabel frageLabel;
    private JLabel punkteLabel;
    private JButton[] antwortButtons = new JButton[3];
    private JButton startButton;
    private JFrame hauptFenster;

    private Socket socket;
    private PrintWriter ausgang;
    private BufferedReader eingang;
    private final int port = 1404;

    private int punkte = 0;
    private String spielerName = "Spieler 1";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizClientMitPunkte::new);
    }

    public QuizClientMitPunkte() {
        initialisiereGUI();
        verbindeMitServer();
    }

    private void initialisiereGUI() {
        hauptFenster = new JFrame("QuizDuell - Wer ist der Beste?");
        hauptFenster.setSize(1200, 600);
        hauptFenster.setLocation(
                (int) (bildschirmAufloesung.getWidth() / 2 - 600),
                (int) (bildschirmAufloesung.getHeight() / 2 - 300)
        );
        hauptFenster.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        hauptFenster.setLayout(new BorderLayout());

        JPanel obenPanel = new JPanel(new BorderLayout());
        frageLabel = new JLabel("Warte auf Spielstart...", SwingConstants.CENTER);
        frageLabel.setFont(new Font("Arial", Font.BOLD, 24));
        obenPanel.add(frageLabel, BorderLayout.CENTER);

        JPanel rechtsPanel = new JPanel();
        rechtsPanel.setLayout(new BoxLayout(rechtsPanel, BoxLayout.Y_AXIS));

        JLabel spielerNameLabel = new JLabel("Spieler: " + spielerName);
        spielerNameLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        rechtsPanel.add(spielerNameLabel);

        punkteLabel = new JLabel("Punkte: " + punkte);
        punkteLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        rechtsPanel.add(punkteLabel);

        obenPanel.add(rechtsPanel, BorderLayout.EAST);
        hauptFenster.add(obenPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1));

        for (int i = 0; i < 3; i++) {
            antwortButtons[i] = new JButton();
            antwortButtons[i].setFont(new Font("Arial", Font.PLAIN, 18));
            final char antwortChar = (char) ('A' + i);
            antwortButtons[i].addActionListener(e -> {
                sendeAntwort(String.valueOf(antwortChar));
                for (JButton btn : antwortButtons) {
                    btn.setEnabled(false);
                }
            });
            antwortButtons[i].setEnabled(false);
            buttonPanel.add(antwortButtons[i]);
        }

        startButton = new JButton("Quiz starten");
        startButton.setFont(new Font("Arial", Font.BOLD, 18));
        startButton.addActionListener(e -> {
            ausgang.println("START");
            startButton.setEnabled(false);
        });
        buttonPanel.add(startButton);

        hauptFenster.add(buttonPanel, BorderLayout.CENTER);
        hauptFenster.setVisible(true);
    }

    private void verbindeMitServer() {
        try {
            socket = new Socket("localhost", port);
            ausgang = new PrintWriter(socket.getOutputStream(), true);
            eingang = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String nachricht;
                    while ((nachricht = eingang.readLine()) != null) {
                        if (nachricht.startsWith("FRAGE|")) {
                            String frageText = nachricht.substring(6);
                            SwingUtilities.invokeLater(() -> frageLabel.setText(frageText));
                        } else if (nachricht.startsWith("PUNKTE|")) {
                            String[] teile = nachricht.split("\\|")[1].split(":");
                            String spieler = teile[0];
                            int neuPunkte = Integer.parseInt(teile[1]);
                            SwingUtilities.invokeLater(() -> {
                                if (spieler.equals(spielerName)) {
                                    punkte = neuPunkte;
                                    punkteLabel.setText("Punkte: " + punkte);
                                }
                            });
                        } else if (nachricht.startsWith("GEWINNER|")) {
                            String gewinnerText = nachricht.substring(9);
                            zeigeNachricht(gewinnerText);
                        } else if (nachricht.startsWith("SPIEL_ENDE|")) {
                            zeigeNachricht("Das Spiel ist zu Ende!");
                        }
                    }
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        zeigeFehler("Verbindungsfehler", "Verbindung zum Server verloren.");
                    }
                }
            }).start();

        } catch (IOException e) {
            zeigeFehler("Verbindungsfehler", "Server nicht erreichbar. Bitte starten Sie den Server zuerst.");
        }
    }

    private void sendeAntwort(String antwort) {
        ausgang.println(antwort);
    }

    private void zeigeNachricht(String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht);
    }

    private void zeigeFehler(String titel, String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht, titel, JOptionPane.ERROR_MESSAGE);
    }
}
