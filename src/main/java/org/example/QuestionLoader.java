package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QuestionLoader {
    public static void main(String[] args) {
        String dateiPfad = "src/Ordner_Fragen/fragen.txt";
        List<String[]> fragenListe = new ArrayList<>();

        File file = new File(dateiPfad);
        System.out.println("Datei existiert? " + file.exists());

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(dateiPfad))) {
            String zeile;
            String[] aktuelleFrage = new String[5]; // Immer Grösse 5 verwenden

            while ((zeile = bufferedReader.readLine()) != null) {
                //zeile = zeile.trim();
                //System.out.println("Gelesen: " + zeile);

                if (zeile.startsWith("#")) {
                    // Neue Frage beginnt - vorherige speichern falls vorhanden
                    if (aktuelleFrage[0] != null && aktuelleFrage[4] != null) {
                        fragenListe.add(aktuelleFrage);
                    }
                    aktuelleFrage = new String[5];
                } else if (zeile.startsWith("A ")) {  // WICHTIG: Leerzeichen nach A/B/C
                    aktuelleFrage[1] = zeile.substring(2).trim();
                } else if (zeile.startsWith("B ")) {
                    aktuelleFrage[2] = zeile.substring(2).trim();
                } else if (zeile.startsWith("C ")) {
                    aktuelleFrage[3] = zeile.substring(2).trim();
                } else if (zeile.startsWith("richtig: ")) {
                    aktuelleFrage[4] = zeile.substring(9).trim();
                } else if (!zeile.isEmpty()) {
                    // Fragentext (nur wenn nicht leer und nicht mit # beginnt)
                    aktuelleFrage[0] = zeile;
                }
            }
            // Letzte Frage hinzufügen
            if (aktuelleFrage[0] != null && aktuelleFrage[4] != null) {
                fragenListe.add(aktuelleFrage);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Ausgabe aller geladenen Fragen
        System.out.println("\nAnzahl geladener Fragen: " + fragenListe.size());
        for (int i = 0; i < fragenListe.size(); i++) {
            String[] frage = fragenListe.get(i);
            System.out.println("\nFrage " + (i+1) + ": " + frage[0]);
            System.out.println("A: " + frage[1]);
            System.out.println("B: " + frage[2]);
            System.out.println("C: " + frage[3]);
            System.out.println("Richtig: " + frage[4]);
        }
    }
}