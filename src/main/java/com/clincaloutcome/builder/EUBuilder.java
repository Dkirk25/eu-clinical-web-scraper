package com.clincaloutcome.builder;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class EUBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EUBuilder.class);

    @Autowired
    private Utils utility;

    public Map<String, List<String>> buildEUListFromWebResults(Elements eudraCTNumber, Map<String, List<String>> listOfResults) {
        List<String> listOfColumns = Arrays.asList("Sponsor Protocol Number:", "Start Date*:", "Sponsor Name:", "Full Title:",
                "Medical condition:", "Disease:", "Population Age:", "Gender:", "Trial results:");

        List<String> mapFields = Arrays.asList("sponsorProtocols", "startDates", "sponsorNames", "fullTitles",
                "medicalConditions", "diseases", "populationAge", "genders", "trialResults");

        String mainEudraCT = "";
        for (Element number : eudraCTNumber) {
            String eudraCT;

            if (number.text().contains("EudraCT Number:")) {
                eudraCT = utility.wordParser(number.text());
                mainEudraCT = eudraCT;
                listOfResults.get("eudraCT").add(eudraCT);
            } else if (number.text().contains("Trial protocol:")) {
                handleTrialProtocol(mainEudraCT, number, listOfResults);
            } else {
                for (int i = 0; i < listOfColumns.size(); i++) {
                    if (number.text().contains(listOfColumns.get(i))) {
                        listOfResults.get(mapFields.get(i)).add(utility.wordParser(number.text()));
                    }
                }
            }
        }
        return listOfResults;
    }

    private void handleTrialProtocol(String eudraCT, Element number, Map<String, List<String>> listOfResults) {
        String protocol = utility.wordParser(number.text());
        listOfResults.get("trialProtocol").add(protocol);
        String firstProtocol = getProtocolType(protocol);
        // Go into link and save endpoints.
        connectAndGrabEndPoints(eudraCT, firstProtocol, listOfResults);
    }

    private String getProtocolType(String protocol) {
        String firstProtocol;
        if (protocol.contains("Outside EU/EEA")) {
            firstProtocol = "3rd";
        } else {
            firstProtocol = protocol.substring(0, 2);
        }
        return firstProtocol;
    }

    private void connectAndGrabEndPoints(String eudraCT, String firstProtocol, Map<String, List<String>> listOfResults) {
        Document doc2;
        String url = "https://www.clinicaltrialsregister.eu/ctr-search/trial/" + eudraCT + "/" + firstProtocol;

        try {
            doc2 = SSLHelper.getConnection(url).get();

            Elements endPoints = doc2.select("#section-e > tbody > tr");

            List<String> allRows = new ArrayList<>();

            endPoints.forEach(string -> allRows.add(string.text()));

            List<String> primary = new ArrayList<>();
            List<String> secondary = new ArrayList<>();

            allRows.forEach(word -> {
                if (word.contains("E.5.1 Primary end point")) {
                    primary.add(word);
                }
            });

            if (primary.isEmpty()) {
                primary.add("None");
            }

            String secondaryEndpoint = "E.5.2 Secondary end point";
            allRows.forEach(word -> {
                if (word.contains(secondaryEndpoint)) {
                    secondary.add(utility.trailParser(word, secondaryEndpoint));
                }
            });

            if (secondary.isEmpty()) {
                secondary.add("None");
            }

            listOfResults.get("primaryEndpoints").add(utility.trailParser(primary.get(0), "E.5.1 Primary end point"));
            listOfResults.get("secondaryEndPoints").add(utility.trailParser(secondary.get(0), secondaryEndpoint));
        } catch (IOException e) {
            LOGGER.error("Bad url for primary and secondary endpoints.");
        }
    }
}
