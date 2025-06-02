package org.Codes;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Der QuizClient1 ist die Client-Seite eines Quizspiels, das mit einem Server kommuniziert.
 * Das GUI zeigt Fragen, Antwortmöglichkeiten und den Punktestand an.
 * Es empfängt Nachrichten vom Server und reagiert entsprechend.
 */
public class QuizClient2 {

    // Bildschirmauflösung zur Zentrierung des Fensters
    private Dimension bildschirmAufloesung = Toolkit.getDefaultToolkit().getScreenSize();

    // GUI-Komponenten
    private JLabel frageLabel;
    private JLabel punkteLabel;
    private JLabel spielerNameLabel;
    private JButton[] antwortButtons = new JButton[3];
    private JButton startButton;
    private JFrame hauptFenster;

    // Netzwerk-Kommunikation
    private Socket socket;
    private PrintWriter ausgang;
    private BufferedReader eingang;

    // Server-Port
    private final int port = 1404;

    // Spielzustand
    private String letzteAntwort;
    private String spielerName = "Spieler 1";
    private int punktzahl = 0;

    /**
     * Hauptmethode, startet die GUI im Event-Dispatch-Thread.
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizClient2::new);
    }

    /**
     * Konstruktor: Initialisiert die GUI und verbindet zum Server.
     */
    public QuizClient2() {
        initialisiereGUI();
        verbindeMitServer();
    }

    /**
     * Initialisiert die grafische Benutzeroberfläche.
     */
    private void initialisiereGUI() {
        // Hauptfenster erstellen
        hauptFenster = new JFrame("QuizDuell - Wer ist der Beste?");
        hauptFenster.setSize(1200, 600);
        hauptFenster.setLocation(
                (int) (bildschirmAufloesung.getWidth() / 2 - 600),
                (int) (bildschirmAufloesung.getHeight() / 2 - 300)
        );
        hauptFenster.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        hauptFenster.setLayout(new BorderLayout());

        // Frage-Label
        frageLabel = new JLabel("Warte auf Spielstart...", SwingConstants.CENTER);
        frageLabel.setFont(new Font("Arial", Font.BOLD, 24));
        hauptFenster.add(frageLabel, BorderLayout.CENTER);

        // Info-Panel mit Spielername und Punktzahl
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        punkteLabel = new JLabel("Punkte: " + punktzahl);
        spielerNameLabel = new JLabel(spielerName);
        infoPanel.add(spielerNameLabel);
        infoPanel.add(punkteLabel);
        hauptFenster.add(infoPanel, BorderLayout.NORTH);

        // Panel für Antwortbuttons und Start-Button
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1));

        // Antwort-Buttons (A, B, C)
        for (int i = 0; i < 3; i++) {
            antwortButtons[i] = new JButton();
            antwortButtons[i].setFont(new Font("Arial", Font.PLAIN, 18));

            final char antwort = (char) ('A' + i);
            // ActionListener für Antwort-Buttons
            antwortButtons[i].addActionListener(e -> {
                letzteAntwort = String.valueOf(antwort);
                sendeAntwort(letzteAntwort);
                // Buttons nach Auswahl deaktivieren
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
     * Stellt die Verbindung zum Server her und startet den Listener-Thread.
     */
    private void verbindeMitServer() {
        try {
            socket = new Socket("localhost", port);
            ausgang = new PrintWriter(socket.getOutputStream(), true);
            eingang = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Thread zum Lesen der Server-Nachrichten
            new Thread(() -> {
                try {
                    String nachricht;
                    while ((nachricht = eingang.readLine()) != null) {
                        if (nachricht.startsWith("STATUS|")) {
                            // Statusmeldung: Warten oder Bereit
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
                        } else if (nachricht.startsWith("SPIELERNAME|")) {
                            // Spielername vom Server setzen
                            spielerName = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                spielerNameLabel.setText(spielerName);
                            });
                        } else if (nachricht.startsWith("FRAGE|")) {
                            // Neue Frage empfangen
                            String frageText = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                frageLabel.setText(frageText);
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(true);
                                }
                            });
                        } else if (nachricht.startsWith("A: ")) {
                            // Antwortmöglichkeit A
                            final String nachrichtA = nachricht;
                            SwingUtilities.invokeLater(() -> antwortButtons[0].setText(nachrichtA));
                        } else if (nachricht.startsWith("B: ")) {
                            // Antwortmöglichkeit B
                            final String nachrichtB = nachricht;
                            SwingUtilities.invokeLater(() -> antwortButtons[1].setText(nachrichtB));
                        } else if (nachricht.startsWith("C: ")) {
                            // Antwortmöglichkeit C
                            final String nachrichtC = nachricht;
                            SwingUtilities.invokeLater(() -> antwortButtons[2].setText(nachrichtC));
                        } else if (nachricht.equals("NÄCHSTE_FRAGE")) {
                            // Nächste Frage vorbereiten
                            SwingUtilities.invokeLater(() -> {
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(false);
                                    button.setText(""); // Optional: Antworttexte löschen
                                }
                            });
                        } else if (nachricht.startsWith("RICHTIG|")) {
                            // Richtig geantwortet
                            String finalNachricht = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                zeigeTemporäreNachricht(finalNachricht, 5000);
                                erholePunkte(true);
                            });
                        } else if (nachricht.startsWith("LANGSAM|")) {
                            // Zeit abgelaufen
                            String finalNachricht = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                zeigeTemporäreNachricht(finalNachricht, 5000);
                            });
                        } else if (nachricht.startsWith("FALSCH|")) {
                            // Falsche Antwort
                            String finalNachricht = nachricht.split("\\|")[1];
                            SwingUtilities.invokeLater(() -> {
                                zeigeTemporäreNachricht("Falsch! " + finalNachricht, 5000);
                            });
                        } else if (nachricht.startsWith("GEWINNER|")) {
                            // Gewinner bekanntgegeben
                            String finalNachricht2 = nachricht;
                            SwingUtilities.invokeLater(() -> {
                                zeigeNachricht(finalNachricht2.split("\\|")[1]);
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(false);
                                }
                            });
                        } else if (nachricht.equals("NEUES_SPIEL")) {
                            // Neues Spiel starten
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
     * Sendet die gewählte Antwort an den Server.
     * @param antwort Die Antwort, die gesendet werden soll.
     */
    private void sendeAntwort(String antwort) {
        ausgang.println(antwort);
    }

    /**
     * Zeigt eine temporäre Nachricht im Fragen-Label an.
     * Nach der Verzögerung wird wieder die ursprüngliche Frage angezeigt.
     * @param nachricht Die Nachricht, die angezeigt werden soll.
     * @param verzögerungMs Dauer in Millisekunden, die die Nachricht angezeigt wird.
     */
    private void zeigeTemporäreNachricht(String nachricht, int verzögerungMs) {
        SwingUtilities.invokeLater(() -> {
            frageLabel.setText(nachricht);
            for (JButton button : antwortButtons) {
                button.setEnabled(false);
                button.setText("");
            }
        });
        Timer timer = new Timer(verzögerungMs, e -> {
            SwingUtilities.invokeLater(() -> ((Timer)e.getSource()).stop());
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Zeigt eine Nachricht im Fragen-Label an.
     * @param nachricht Die Nachricht, die angezeigt wird.
     */
    private void zeigeNachricht(String nachricht) {
        frageLabel.setText(nachricht);
    }

    /**
     * Zeigt eine Fehlermeldung in einem Dialog.
     * @param titel Der Titel des Fehlermeldungsdialogs.
     * @param nachricht Der Text der Fehlermeldung.
     */
    private void zeigeFehler(String titel, String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht, titel, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Aktualisiert die Punktzahl basierend auf der Antwort.
     * @param richtig Ob die Antwort richtig war.
     */
    private void erholePunkte(boolean richtig) {
        if (richtig) {
            punktzahl += 1;
        }
        punkteLabel.setText("Punkte: " + punktzahl);
    }
}
