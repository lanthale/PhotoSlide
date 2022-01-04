/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.photoslide.browsermetadata;

import fr.dudie.nominatim.client.JsonNominatimClient;
import fr.dudie.nominatim.model.Address;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.net.ssl.SSLContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.photoslide.ExportDialogController;

/**
 *
 * @author cleme
 */
public class GeoCoding {

    private final String baseUrl = "https://nominatim.openstreetmap.org";
    private final String email = "clemens.lanthaler@itarchitects.at";
    private JsonNominatimClient nominatimClient;
    private List<Address> lastSearchResult;
    private Address lastSearchGPSResult;

    public GeoCoding() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        SSLContext context = SSLContexts.createDefault();
        SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(context, NoopHostnameVerifier.INSTANCE);
        builder.setSSLSocketFactory(sslConnectionFactory);

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslConnectionFactory)
                .build();

        HttpClientConnectionManager ccm = new BasicHttpClientConnectionManager(registry);

        builder.setConnectionManager(ccm);
        CloseableHttpClient httpClient = builder.build();
        nominatimClient = new JsonNominatimClient(baseUrl, httpClient, email);
    }

    /**
     *
     * @param name the name to search for e.g. a city, street, ...
     * @return a list of all found addresses. The first address matches at most.
     * The name is stored in displayname property
     */
    public ObservableList<Address> geoSearchForNameAsAddress(String name) {
        if (name == null) {
            return FXCollections.emptyObservableList();
        }
        if (name.equalsIgnoreCase("")) {
            return FXCollections.emptyObservableList();
        }
        try {
            //final Address address = nominatimClient.getAddress(1.64891269513038, 48.1166561643464);
            final List<Address> addresses = nominatimClient.search(name);
            return FXCollections.observableArrayList(addresses);
        } catch (IOException ex) {
            Logger.getLogger(ExportDialogController.class.getName()).log(Level.SEVERE, null, ex);
            return FXCollections.emptyObservableList();
        }
    }

    public ObservableList<String> geoSearchForNameAsStrings(String name) {
        if (name == null) {
            return FXCollections.emptyObservableList();
        }
        if (name.equalsIgnoreCase("")) {
            return FXCollections.emptyObservableList();
        }
        ObservableList<String> retList = FXCollections.observableArrayList();
        try {
            //final Address address = nominatimClient.getAddress(1.64891269513038, 48.1166561643464);
            lastSearchResult = nominatimClient.search(name);
            for (Address addresse : lastSearchResult) {
                retList.add(addresse.getDisplayName());
            }
            return retList;
        } catch (IOException ex) {
            Logger.getLogger(ExportDialogController.class.getName()).log(Level.SEVERE, null, ex);
            return FXCollections.emptyObservableList();
        }
    }

    /**
     *
     * @param longitude longitude of the address
     * @param latitude latitude of the address
     * @return the found address. Call getDisplayName to get string of the found
     * address
     */
    public Address geoSearchForGPS(double longitude, double latitude) {
        if (longitude < 0 || latitude < 0) {
            return null;
        }
        if (longitude == 0 || latitude == 0) {
            return null;
        }
        try {
            lastSearchGPSResult = nominatimClient.getAddress(longitude, latitude);
            return lastSearchGPSResult;
        } catch (IOException ex) {
            Logger.getLogger(ExportDialogController.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    /**
     *
     * @return the client to do the resolution of any address
     */
    public JsonNominatimClient getNominatimClient() {
        return nominatimClient;
    }

    /**
     *
     * @return results the list found for the given text during string search in
     * DB
     */
    public List<Address> getLastSearchResult() {
        return lastSearchResult;
    }

    /**
     *
     * @return if searched for lat/long value this item represents the resulting
     * object
     */
    public Address getLastSearchGPSResult() {
        return lastSearchGPSResult;
    }

    public void setLastSearchGPSResult(Address lastSearchGPSResult) {
        this.lastSearchGPSResult = lastSearchGPSResult;
    }

}
