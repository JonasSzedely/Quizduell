package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class QuizClient2 {
    private Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    private JLabel fragenNummer, frage;
    private JButton[] ant = new JButton[3]; // A, B, C Antwort Button
    private JButton startButton;
    private JFrame w1;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final int port = 1404; // Port für die Verbindung
    private boolean quizStarted = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizClient2::new); // GUI im Event Dispatch Thread starten
    }

    public QuizClient2() {
        w1 = new JFrame("QuizDuell, Wer ist der Beste!!!");
        w1.setSize(1200, 600);
        w1.setLocation((int) (dim.getWidth() / 2 - 400), (int) (dim.getHeight() / 2 - 300));
        w1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        w1.setLayout(new GridLayout(3, 1));

        // Frage-Label für Fragen Nummer initialisieren
        fragenNummer = new JLabel("Hier steht die FragenNr.", SwingConstants.CENTER);
        fragenNummer.setFont(new Font("Arial", Font.BOLD, 24)); // Schriftgröße erhöhen
        w1.add(fragenNummer); // Frage oben platzieren

        // Frage-Label initialisieren
        frage = new JLabel("Hier steht die Frage selbst", SwingConstants.CENTER);
        frage.setFont(new Font("Arial", Font.BOLD, 18)); // Schriftgröße erhöhen
        w1.add(frage); // Frage in der Mitte platzieren

        // Panel für die Antwort-Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(4, 1));

        // Buttons initialisieren und zum Panel hinzufügen
        for (int i = 0; i < 3; i++) { // Nur 3 Antworten (A, B, C)
            ant[i] = new JButton("Antwort: " + (char) ('A' + i)); // Setze Button-Text auf "Antwort: A", "Antwort: B", "Antwort: C"
            final int index = i;
            ant[i].addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String answer = String.valueOf((char) ('A' + index)); // Die gewählte Antwort
                    sendAnswer(answer.trim()); // Sende die gewählte Antwort an den Server
                    // Deaktiviere die Buttons, während auf die Antwort gewartet wird
                    for (JButton button : ant) {
                        button.setEnabled(false);
                    }
                }
            });
            ant[i].setEnabled(false);
            buttonPanel.add(ant[i]);
        }

        // Start-Button initialisieren
        startButton = new JButton("Starten");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startQuiz();
            }
        });
        buttonPanel.add(startButton, SwingConstants.BOTTOM);

        w1.add(buttonPanel); // Button-Panel unten platzieren
        w1.setVisible(true);
        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(w1, "Verbindung zum Server konnte nicht hergestellt werden. Bitte sicherstellen, dass der Server läuft und der Port korrekt ist.");
            System.exit(1); // Programm beenden, wenn die Verbindung nicht hergestellt werden kann
        }
    }

    private void startQuiz() {
        quizStarted = true;
        startButton.setEnabled(false);
        for (JButton button : ant) {
            button.setEnabled(true);
        }
        displayQuestions();
    }

    private void displayQuestions() {
        new Thread(() -> { // Erstelle einen neuen Thread für die Fragenanzeige
            try {
                String line;
                while (quizStarted) {
                    String frageNr = in.readLine(); // FrageNr empfangen
                    if (frageNr == null) {
                        break; // Beende die Schleife, wenn keine Fragen mehr vorhanden sind
                    }
                    SwingUtilities.invokeLater(() -> fragenNummer.setText(frageNr)); // FrageNr aktualisieren

                    String frageText = in.readLine(); // Frage selbst empfangen
                    if (frageText == null) {
                        JOptionPane.showMessageDialog(w1, "Fehler beim Empfangen der Frage. Bitte überprüfen Sie den Server.");
                        return;
                    }
                    SwingUtilities.invokeLater(() -> frage.setText(frageText)); // Frage aktualisieren

                    // Antworten empfangen
                    for (int i = 0; i < 3; i++) {
                        line = in.readLine();
                        if (line == null) {
                            JOptionPane.showMessageDialog(w1, "Fehler beim Empfangen der Antworten. Bitte überprüfen Sie den Server.");
                            return;
                        }
                        final String antwortText = line.split(": ")[1]; // Extrahiere die Antwort nach dem ": "
                        final int finalI = i;
                        SwingUtilities.invokeLater(() -> ant[finalI].setText("Antwort: " + antwortText)); // Antwort-Buttons aktualisieren
                    }

                    // Warte auf die Rückmeldung des Servers (Richtig/Falsch)
                    String responseMessage = in.readLine();
                    if (responseMessage != null) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(w1, responseMessage);
                            // Frage-Buttons wieder aktivieren, um die nächste Frage zu ermöglichen
                            for (JButton button : ant) {
                                button.setEnabled(true);
                            }
                        });
                    }
                }

                // Wenn keine Fragen mehr vorhanden sind
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(w1, "Das Spiel ist beendet! Ihre Ergebnisse werden ausgewertet..."); // Nachricht anzeigen
                    startButton.setEnabled(true); // Startbutton wieder aktivieren
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendAnswer(String answer) {
        out.println(answer); // Sende die Antwort an den Server
    }
}
