package de.uhd.campuscoffee.service;

import de.uhd.campuscoffee.entity.PointOfSale;
import de.uhd.campuscoffee.repository.PointOfSaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;

@Service
public class OsmImportService {

    private static final String OSM_API_URL = "https://www.openstreetmap.org/api/0.6/node/";

    private final PointOfSaleRepository pointOfSaleRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public OsmImportService(PointOfSaleRepository pointOfSaleRepository, RestTemplate restTemplate) {
        this.pointOfSaleRepository = pointOfSaleRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Importiert einen Point-of-Sale aus OpenStreetMap basierend auf einer Node-ID.
     *
     * @param nodeId Die OSM Node-ID
     * @return Der erstellte PointOfSale
     * @throws IllegalArgumentException wenn der Name fehlt oder andere Fehler auftreten
     */
    public PointOfSale importFromOsm(String nodeId) {
        try {
            // Lade OSM-XML via HTTP
            String url = OSM_API_URL + nodeId;
            String xmlResponse = restTemplate.getForObject(url, String.class);

            if (xmlResponse == null || xmlResponse.isEmpty()) {
                throw new IllegalArgumentException("Keine Antwort von OpenStreetMap API erhalten");
            }

            // Parse XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            Element root = document.getDocumentElement();
            NodeList nodeList = root.getElementsByTagName("node");

            if (nodeList.getLength() == 0) {
                throw new IllegalArgumentException("Keine Node in der OSM-XML gefunden");
            }

            Element nodeElement = (Element) nodeList.item(0);

            // Extrahiere Koordinaten
            String latStr = nodeElement.getAttribute("lat");
            String lonStr = nodeElement.getAttribute("lon");

            if (latStr == null || latStr.isEmpty() || lonStr == null || lonStr.isEmpty()) {
                throw new IllegalArgumentException("Koordinaten nicht gefunden in OSM-Node");
            }

            BigDecimal latitude = new BigDecimal(latStr);
            BigDecimal longitude = new BigDecimal(lonStr);

            // Extrahiere Name aus Tags
            String name = null;
            NodeList tagList = nodeElement.getElementsByTagName("tag");
            for (int i = 0; i < tagList.getLength(); i++) {
                Element tagElement = (Element) tagList.item(i);
                String key = tagElement.getAttribute("k");
                if ("name".equals(key)) {
                    name = tagElement.getAttribute("v");
                    break;
                }
            }

            // Validierung: Name ist erforderlich
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("OSM-Node enthält keinen Namen (name-Tag fehlt)");
            }

            // Erzeuge neuen POS
            PointOfSale pointOfSale = new PointOfSale();
            pointOfSale.setName(name);
            pointOfSale.setLatitude(latitude);
            pointOfSale.setLongitude(longitude);

            // Speichere über Repository
            return pointOfSaleRepository.save(pointOfSale);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Importieren von OSM-Node: " + e.getMessage(), e);
        }
    }
}


