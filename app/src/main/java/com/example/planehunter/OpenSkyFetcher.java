package com.example.planehunter;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class OpenSkyFetcher {

    public interface PlanesCallback {
        void onPlanesFetched(ArrayList<Plane> planes);
    }

    private double latCenter = 31.9936;
    private double lonCenter = 34.8828;
    private double radiusKm = 4; //? need to change after testing

    public void fetchPlanes(double latCenter, double lonCenter,PlanesCallback callback) {
        new Thread(() -> {
            ArrayList<Plane> planes = new ArrayList<>();
            try {
                if(latCenter !=0 && lonCenter !=0 ){
                    this.latCenter = latCenter;
                    this.lonCenter = lonCenter;
                }

                double[] bbox = boundingBox(this.latCenter, this.lonCenter, radiusKm);

                //the URL for the API request(using REST API)
                String urlString = String.format(
                        "https://opensky-network.org/api/states/all?lamin=%f&lamax=%f&lomin=%f&lomax=%f",
                        bbox[0], bbox[1], bbox[2], bbox[3]
                );

                //Creates the connection using GET request
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())); //Used to read the data from the stream
                StringBuilder content = new StringBuilder();
                String line;

                while ((line = in.readLine()) != null){ //adds each line(Json element) to the stringBuilder
                    content.append(line);
                }
                in.close(); //close stream
                connection.disconnect();

                JSONObject data = new JSONObject(content.toString());
                JSONArray states = data.optJSONArray("states"); //create JsonArray from the states

                if (states != null) {
                    for (int i = 0; i < states.length(); i++) {
                        if(states.optBoolean(8)){ //on ground
                            break;
                        }
                        JSONArray state = states.getJSONArray(i);
                        double lat = state.optDouble(6, Double.NaN);
                        double lon = state.optDouble(5, Double.NaN);
                        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                            double distance = haversine(latCenter, lonCenter, lat, lon);
                            if (distance <= radiusKm) {
                                String callSign = state.optString(1, "N/A").trim();
                                String icao = state.getString(0);
                                double altitude = state.optDouble(7, 0);
                                planes.add(new Plane(icao, callSign, lat, lon, altitude));
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            new Handler(Looper.getMainLooper()).post(() -> callback.onPlanesFetched(planes));

        }).start();
    }

    private double[] boundingBox(double lat, double lon, double radiusKm) {
        double deltaLat = radiusKm / 111.0;
        double deltaLon = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - deltaLat, lat + deltaLat, lon - deltaLon, lon + deltaLon};
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
