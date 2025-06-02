package org.Codes;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Ein Quiz-Client, der sich mit dem Quiz-Server verbindet und ein GUI für die Spielinteraktion bereitstellt.
 * Das Spiel startet automatisch für alle Clients, sobald beide Client den Start-Button gedrückt haben.
 */
public class QuizClient1 {
    /** Bildschirmauflösung für die GUI-Positionierung */
    private Dimension bildschirmAufloesung = Toolkit.getDefaultToolkit().getScreenSize();

    /** Label für die Anzeige der Frage */
    private JLabel frageLabel;

    /** Label für die Anzeige der Punkte */
    private JLabel punkteLabel;

    /** Label für den Spielernamen */
    private JLabel spielerNameLabel;

    /** Buttons für die Antwortmöglichkeiten (A, B, C) */
    private JButton[] antwortButtons = new JButton[3];

    /** Button zum Starten des Quiz-Spiels */
    private JButton startButton;

    /** Hauptfenster der Anwendung */
    private JFrame hauptFenster;

    /** Socket für die Verbindung zum Server */
    private Socket socket;

    /** Ausgabestream zum Senden von Nachrichten an den Server */
    private PrintWriter ausgang;

    /** Eingabestream zum Empfangen von Nachrichten vom Server */
    private BufferedReader eingang;

    /** Portnummer für die Serververbindung */
    private final int port = 1404;

    /** Speichert die letzte vom Spieler gegebene Antwort */
    private String letzteAntwort;

    /** Spielername */
    private String spielerName = "Spieler 1";

    /** Aktuelle Punktzahl des Spielers */
    private int punktzahl = 0;

    /**
     * Hauptmethode zum Starten des Clients.
     * Diese Methode wird automatisch von der Java-Laufzeitumgebung aufgerufen.
     *
     * @param args Keine Eingabeparameter erforderlich
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizClient1::new);
    }

    /**
     * Konstruktor für den QuizClient.
     * Initialisiert die GUI und stellt die Verbindung zum Quiz-Server her.
     */
    public QuizClient1() {
        initialisiereGUI();
        verbindeMitServer();
    }

    /**
     * Initialisiert die GUI-Komponenten des Clients.
     * Dazu gehören Fenster, Labels, Buttons und deren Layout.
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

        // Frage-Label (zentriert mit größerer Schrift)
        frageLabel = new JLabel("Warte auf Spielstart...", SwingConstants.CENTER);
        frageLabel.setFont(new Font("Arial", Font.BOLD, 24));
        hauptFenster.add(frageLabel, BorderLayout.CENTER);

        // Panel für die Punkte und den Spielernamen
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        punkteLabel = new JLabel("Punkte: " + punktzahl);  // Initialwert der Punkte
        spielerNameLabel = new JLabel(spielerName); // Spielername zuweisen
        infoPanel.add(spielerNameLabel);
        infoPanel.add(punkteLabel);
        hauptFenster.add(infoPanel, BorderLayout.NORTH); // Hinzufügen des Panels an die obere Kante

        // Panel für die Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1));

        // Antwort-Buttons
        for (int i = 0; i < 3; i++) {
            antwortButtons[i] = new JButton();
            antwortButtons[i].setFont(new Font("Arial", Font.PLAIN, 18));
            final char antwort = (char) ('A' + i);  // final hinzugefügt
            antwortButtons[i].addActionListener(e -> {
                letzteAntwort = String.valueOf(antwort); // Antwort speichern
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
     * Stellt eine Verbindung zum Quiz-Server her.
     * Versucht, sich mit dem Server zu verbinden und initialisiert die Streams für die Kommunikation.
     * Bei Verbindungsproblemen wird ein Fehlerdialog angezeigt.
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
                        } else if (nachricht.startsWith("SPIELERNAME|")) {
                            spielerName = nachricht.split("\\|")[1]; // Setze den Spielernamen
                            SwingUtilities.invokeLater(() -> {
                                spielerNameLabel.setText(spielerName); // Aktualisiere das Label
                            });
                        } else if (nachricht.startsWith("FRAGE|")) {
                            String frageText = nachricht.split("\\|")[1];  // final nicht nötig, da neu zugewiesen
                            SwingUtilities.invokeLater(() -> {
                                frageLabel.setText(frageText);
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(true);
                                }
                            });
                        } else if (nachricht.startsWith("A: ")) {
                            final String nachrichtA = nachricht;  // final Kopie
                            SwingUtilities.invokeLater(() -> antwortButtons[0].setText(nachrichtA));
                        } else if (nachricht.startsWith("B: ")) {
                            final String nachrichtB = nachricht;  // final Kopie
                            SwingUtilities.invokeLater(() -> antwortButtons[1].setText(nachrichtB));
                        } else if (nachricht.startsWith("C: ")) {
                            final String nachrichtC = nachricht;  // final Kopie
                            SwingUtilities.invokeLater(() -> antwortButtons[2].setText(nachrichtC));
                        } else if (nachricht.startsWith("RICHTIG|")) {
                            String finalNachricht = nachricht;
                            SwingUtilities.invokeLater(() -> {
                                zeigeNachricht(finalNachricht.split("\\|")[1]);
                                erholePunkte(true); // Punkte erhöhen
                            });
                        } else if (nachricht.startsWith("ersterRichtigerSpieler")) {
                            String finalNachricht = nachricht;
                            SwingUtilities.invokeLater(() -> {
                                zeigeNachricht(finalNachricht.split("\\|")[1]);
                                erholePunkte(true); // Punkte erhöhen
                            });
                        } else if (nachricht.startsWith("FALSCH|")) {
                            String finalNachricht1 = nachricht;
                            SwingUtilities.invokeLater(() -> {
                                zeigeNachricht(finalNachricht1.split("\\|")[1]);
                                // Keine Punkte verringern, einfach ignorieren
                            });
                        } else if (nachricht.startsWith("GEWINNER|")) {
                            String finalNachricht2 = nachricht;
                            SwingUtilities.invokeLater(() -> {
                                zeigeNachricht(finalNachricht2.split("\\|")[1]);
                                for (JButton button : antwortButtons) {
                                    button.setEnabled(false);
                                }
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
     * Sendet die Antwort des Spielers an den Server.
     *
     * @param antwort Die ausgewählte Antwort (A, B oder C)
     */
    private void sendeAntwort(String antwort) {
        ausgang.println(antwort);
    }

    /**
     * Zeigt eine Nachricht in einem Dialog an.
     *
     * @param nachricht Die anzuzeigende Nachricht
     */
    private void zeigeNachricht(String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht);
    }

    /**
     * Zeigt einen Fehlerdialog an.
     *
     * @param titel FehleranzeigeDialog
     * @param nachricht Die Fehlernachricht
     */
    private void zeigeFehler(String titel, String nachricht) {
        JOptionPane.showMessageDialog(hauptFenster, nachricht, titel, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Aktualisiert die Punktzahl des Spielers.
     * Wenn die Antwort richtig war, erhöht sich die Punktzahl um 1.
     * Aktualisiert zudem die Anzeige der aktuellen Punktzahl.
     *
     * @param richtig Wenn die Antwort richtig war, erhöhen wir die Punkte
     */
    private void erholePunkte(boolean richtig) {
        if (richtig) {
            punktzahl += 1; // Erhöhung der Punktzahl um 1 für eine richtige Antwort
        }
        // Keine Punkte für falsche Antworten! --> ignorieren

        // Aktualisiere das Punkte-Label
        punkteLabel.setText("Punkte: " + punktzahl);
    }
}
