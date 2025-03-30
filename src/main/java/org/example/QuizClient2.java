package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class QuizClient2 {
    private Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    private JLabel frage;
    private JButton[] ant = new JButton[3]; // A, B, C Antwort Button
    private JButton startButton;
    private JFrame w1;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int port = 1404; // Iranische Kalender Jahr als Port-Schlüssel gemerkt. :-). ---> nicht vorreserviert in bekannte Netwerkdiensten.
    private boolean quizStarted = false;

    public static void main(String[] args) {
        new QuizClient2();
    }

    public QuizClient2() {
        w1 = new JFrame("QuizDuell, Wer ist der Beste!!!");
        w1.setSize(800, 600);
        w1.setLocation((int) (dim.getWidth() / 2 - 400), (int) (dim.getHeight() / 2 - 300));
        w1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        w1.setLayout(new BorderLayout());

        // Frage-Label initialisieren
        frage = new JLabel("Hier steht die Frage", SwingConstants.CENTER);
        frage.setFont(new Font("Arial", Font.BOLD, 24)); // Schriftgröße erhöhen
        w1.add(frage, BorderLayout.NORTH); // Frage oben platzieren

        // Panel für die Antwort-Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(4, 1)); // 4 Zeilen, 1 Spalte

        // Buttons initialisieren und zum Panel hinzufügen
        for (int i = 0; i < 3; i++) { // Nur 3 Antworten (A, B, C)
            ant[i] = new JButton("Antwort: " + (char) ('A' + i)); // Setze Button-Text auf "Antwort: A", "Antwort: B", "Antwort: C"
            final int index = i;
            ant[i].addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String answer = String.valueOf((char) ('A' + index)); // Die gewählte Antwort
                    sendAnswer(answer); // Sende die gewählte Antwort an den Server
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
        buttonPanel.add(startButton);

        w1.add(buttonPanel, BorderLayout.CENTER);

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
                while (quizStarted && (line = in.readLine()) != null) {
                    // Setze die Frage
                    frage.setText(line); // Setze die Frage
                    System.out.println("Frage empfangen: " + line);

                    String[] antworten = new String[3];

                    for (int i = 0; i < 3; i++) {
                        line = in.readLine();
                        if (line == null) {
                            JOptionPane.showMessageDialog(w1, "Fehler beim Empfangen der Antworten. Bitte überprüfen Sie den Server.");
                            return;
                        }
                        antworten[i] = line; // Speichere die Antwort in der Variablen
                        System.out.println("Antwort empfangen: " + antworten[i]);
                        ant[i].setText("Antwort: " + antworten[i]);
                    }

                    // Lese die richtige Antwort (vierte Zeile)
                    String richtigeAntwort = in.readLine();
                    System.out.println("Richtige Antwort empfangen: " + richtigeAntwort);

                    // Warte auf die Rückmeldung des Servers (Richtig/Falsch)
                    String responseMessage = in.readLine();

                    // Zeige die Rückmeldung an und warte auf die Benutzeraktion
                    JOptionPane.showMessageDialog(w1, responseMessage);

                    for (JButton button : ant) {
                        button.setEnabled(true);
                    }
                }

                // Wenn keine Fragen mehr vorhanden sind
                JOptionPane.showMessageDialog(w1, "Bitte warten, Ihre Ergebnisse werden ausgewertet..."); // Nachricht anzeigen
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void processResponse(String responseMessage) {
        JOptionPane.showMessageDialog(w1, responseMessage);

        for (JButton button : ant) {
            button.setEnabled(true);
        }
    }

    private void sendAnswer(String answer) {
        out.println(answer); // Sende die Antwort an den Server
        // Warte auf die Bestätigung des Servers, dass die Antwort empfangen wurde
        new Thread(() -> {
            try {
                String responseMessage = in.readLine();
                JOptionPane.showMessageDialog(w1, responseMessage);

                for (JButton button : ant) {
                    button.setEnabled(true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
