package org.Codes;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class QuizClient2 {
    private Dimension bildschirmAufloesung = Toolkit.getDefaultToolkit().getScreenSize();
    private JLabel frageLabel;
    private JLabel punkteLabel;
    private JLabel spielerNameLabel;
    private JButton[] antwortButtons = new JButton[3];
    private JButton startButton;
    private JFrame hauptFenster;
    private Socket socket;
    private PrintWriter ausgang;
    private BufferedReader eingang;
    private final int port = 1404;
    private String letzteAntwort;
    private String spielerName = "Spieler 1";
    private int punktzahl = 0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizClient2::new);
    }

    public QuizClient2() {
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

        frageLabel = new JLabel("Warte auf Spielstart...", SwingConstants.CENTER);
        frageLabel.setFont(new Font("Arial", Font.BOLD, 24));
        hauptFenster.add(frageLabel, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        punkteLabel = new JLabel("Punkte: " + punktzahl);
        spielerNameLabel = new JLabel(spielerName);
        infoPanel.add(spielerNameLabel);
        infoPanel.add(punkteLabel);
        hauptFenster.add(infoPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1));

        for (int i = 0; i < 3; i++) {
            antwortButtons[i] = new JButton();
            antwortButtons[i].setFont(new Font("Arial", Font.PLAIN, 18));
            final char antwort = (char) ('A' + i);
            antwortButtons[i].addActionListener(e -> {
                letzteAntwort = String.valueOf(antwort);
                sendeAntwort(letzteAntwort);
                for (JButton button : antwortButtons) {
                    button.setEnabled(false);
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

        hauptFenster.add(buttonPanel, BorderLayout.SOUTH);
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
                        if (nachricht.startsWith("STATUS|")) {
                            String status = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                if (status.startsWith("WARTEN")) {
                                    frageLabel.setText(status.split("\\|")[1]);
                                    startButton.setEnabled(false);
                                } else if (status.startsWith("BEREIT")) {
                                    frageLabel.setText(status.split("\\|")[1]);
                                    startButton.setEnabled(true);
                                }
                            });
                        }
                        else if (nachricht.startsWith("SPIELERNAME|")) {
                            spielerName = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                spielerNameLabel.setText(spielerName);
                            });
                        }
                        else if (nachricht.startsWith("FRAGE|")) {
                            String frageText = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                frageLabel.setText(frageText);
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(true);
                                }
                            });
                        }
                        else if (nachricht.startsWith("A: ")) {
                            final String nachrichtA = nachricht;
                            SwingUtilities.invokeLater(() -> antwortButtons[0].setText(nachrichtA));
                        }
                        else if (nachricht.startsWith("B: ")) {
                            final String nachrichtB = nachricht;
                            SwingUtilities.invokeLater(() -> antwortButtons[1].setText(nachrichtB));
                        }
                        else if (nachricht.startsWith("C: ")) {
                            final String nachrichtC = nachricht;
                            SwingUtilities.invokeLater(() -> antwortButtons[2].setText(nachrichtC));
                        }
                        else if (nachricht.equals("NÄCHSTE_FRAGE")) {
                            SwingUtilities.invokeLater(() -> {
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(false);
                                    button.setText(""); // Optional: Antworttexte löschen
                                }
                            });
                        }
                        else if (nachricht.startsWith("RICHTIG|")) {
                            String finalNachricht = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                zeigeTemporäreNachricht(finalNachricht, 5000);
                                erholePunkte(true);
                            });
                        }
                        else if (nachricht.startsWith("LANGSAM|")) {
                            String finalNachricht = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                zeigeTemporäreNachricht(finalNachricht, 5000);
                            });
                        }
                        else if (nachricht.startsWith("FALSCH|")) {
                            String finalNachricht = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                zeigeTemporäreNachricht("Falsch! " + finalNachricht, 5000);
                            });
                        }
                        else if (nachricht.startsWith("GEWINNER|")) {
                            String finalNachricht2 = nachricht;
                            SwingUtilities.invokeLater(() -> {
                                zeigeNachricht(finalNachricht2.split("\\|")[1]);
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(false);
                                }
                            });
                        } else if (nachricht.equals("NEUES_SPIEL")) {
                            SwingUtilities.invokeLater(() -> {
                                startButton.setEnabled(true);
                                frageLabel.setText("Warte auf Spielstart...");
                                punktzahl = 0;
                                punkteLabel.setText("Punkte: 0");
                            });
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

    private void zeigeTemporäreNachricht(String nachricht, int verzögerungMs) {
        SwingUtilities.invokeLater(() -> {
            frageLabel.setText(nachricht);
            for (JButton button : antwortButtons) {
                button.setEnabled(false);
                button.setText("");
            }
        });

        Timer timer = new Timer(verzögerungMs, null);
        timer.setRepeats(false);
        timer.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> ((Timer)e.getSource()).stop());
        });
        timer.start();
    }

    private void zeigeNachricht(String nachricht) {
        frageLabel.setText(nachricht);
    }

    private void zeigeFehler(String titel, String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht, titel, JOptionPane.ERROR_MESSAGE);
    }

    private void erholePunkte(boolean richtig) {
        if (richtig) {
            punktzahl += 1;
        }
        punkteLabel.setText("Punkte: " + punktzahl);
    }
}