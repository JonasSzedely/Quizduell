package org.Codes;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Der QuizClient2 ist ein Java Swing-Client für ein Quizspiel, der sich mit einem Server verbindet,
 * Fragen empfängt, Antworten sendet und den Spielstand anzeigt.
 */
public class QuizClient2 {
    /** Bildschirmauflösung des Nutzers */
    private Dimension bildschirmAufloesung = Toolkit.getDefaultToolkit().getScreenSize();
    /** Label für die Anzeige der aktuellen Frage */
    private JLabel frageLabel;
    /** Label für die Anzeige der Punktzahl */
    private JLabel punkteLabel;
    /** Label für den Namen des Spielers */
    private JLabel spielerNameLabel;
    /** Array der Antwort-Buttons (A, B, C) */
    private JButton[] antwortButtons = new JButton[3];
    /** Button zum Starten des Spiels */
    private JButton startButton;
    /** Hauptfenster der GUI */
    private JFrame hauptFenster;
    /** Socket für die Serververbindung */
    private Socket socket;
    /** PrintWriter zum Senden von Daten an den Server */
    private PrintWriter ausgang;
    /** BufferedReader zum Empfangen von Daten vom Server */
    private BufferedReader eingang;
    /** Server-Portnummer */
    private final int port = 1404;
    /** Letzte ausgewählte Antwort */
    private String letzteAntwort;
    /** Name des Spielers */
    private String spielerName = "Spieler 1";
    /** Punktestand des Spielers */
    private int punktzahl = 0;

    /**
     * Hauptmethode zum Starten des Clients.
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizClient2::new);
    }

    /**
     * Konstruktor initialisiert die GUI und verbindet mit dem Server.
     */
    public QuizClient2() {
        initialisiereGUI();
        verbindeMitServer();
    }

    /**
     * Initialisiert die grafische Oberfläche des Clients.
     */
    private void initialisiereGUI() {
        hauptFenster = new JFrame("QuizDuell - Wer ist der Beste?");
        hauptFenster.setSize(1200, 600);
        hauptFenster.setLocation(
                (int) (bildschirmAufloesung.getWidth() / 2 - 600),
                (int) (bildschirmAufloesung.getHeight() / 2 - 300)
        );
        hauptFenster.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        hauptFenster.setLayout(new BorderLayout());

        // Frage-Label zentriert und fett
        frageLabel = new JLabel("Warte auf Spielstart...", SwingConstants.CENTER);
        frageLabel.setFont(new Font("Arial", Font.BOLD, 24));
        hauptFenster.add(frageLabel, BorderLayout.CENTER);

        // Info-Leiste mit Name und Punkte
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        punkteLabel = new JLabel("Punkte: " + punktzahl);
        spielerNameLabel = new JLabel(spielerName);
        infoPanel.add(spielerNameLabel);
        infoPanel.add(punkteLabel);
        hauptFenster.add(infoPanel, BorderLayout.NORTH);

        // Panel für Antwort-Buttons und Start-Button
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1));

        // Antwort-Buttons A, B, C
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

        // Start-Button
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

    /**
     * Verbindet den Client mit dem Server und startet den Listener-Thread.
     */
    private void verbindeMitServer() {
        try {
            socket = new Socket("localhost", port);
            ausgang = new PrintWriter(socket.getOutputStream(), true);
            eingang = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String nachricht;
                    while ((nachricht = eingang.readLine()) != null) {
                        // Verarbeitung verschiedener Servernachrichten
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

    /**
     * Sendet die ausgewählte Antwort an den Server.
     * @param antwort Die vom Spieler gewählte Antwort (z.B. "A", "B", "C")
     */
    private void sendeAntwort(String antwort) {
        ausgang.println(antwort);
    }

    /**
     * Zeigt eine temporäre Nachricht im Fragen-Label für eine bestimmte Dauer.
     * Nach Ablauf der Zeit wird die ursprüngliche Frage wieder angezeigt.
     * @param nachricht Die anzuzeigende Nachricht
     * @param verzögerungMs Dauer in Millisekunden, wie lange die Nachricht angezeigt wird
     */
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
            SwingUtilities.invokeLater(() -> {
                // Nach Ablauf der Zeit wieder auf die ursprüngliche Frage setzen oder leeren Text
                // Hier könnte man z.B. die ursprüngliche Frage erneut setzen, falls gespeichert
                // Für jetzt einfach nur den Text zurücksetzen
                frageLabel.setText("Warte auf nächste Frage...");
                ((Timer)e.getSource()).stop();
            });
        });
        timer.start();
    }

    /**
     * Zeigt eine einfache Nachricht im Fragen-Label.
     * @param nachricht Die anzuzeigende Nachricht
     */
    private void zeigeNachricht(String nachricht) {
        frageLabel.setText(nachricht);
    }

    /**
     * Zeigt eine Fehlermeldung in einem Dialogfenster.
     * @param titel Der Titel des Fehlerdialogs
     * @param nachricht Die Fehlermeldung
     */
    private void zeigeFehler(String titel, String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht, titel, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Aktualisiert die Punktzahl basierend auf der Richtigkeit der Antwort.
     * @param richtig true, wenn die Antwort richtig war, sonst false
     */
    private void erholePunkte(boolean richtig) {
        if (richtig) {
            punktzahl += 1;
        }
        punkteLabel.setText("Punkte: " + punktzahl);
    }
}
