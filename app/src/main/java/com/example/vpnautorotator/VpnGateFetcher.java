package com.example.vpnautorotator;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VpnGateFetcher {

    private static final String TAG = "VpnGateFetcher";
    private static final String API_URL = "https://www.vpngate.net/api/iphone/";

    public static List<VpnRotatorService.VpnServer> fetchServers(Context context) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: " + connection.getResponseCode());
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse CSV-like response (skip first 2 lines)
            String[] lines = response.toString().split("\n");
            if (lines.length < 3) return Collections.emptyList();

            List<VpnRotatorService.VpnServer> servers = new ArrayList<>();
            String[] headers = lines[0].split(",");

            // Find column indices
            Map<String, Integer> columnMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnMap.put(headers[i].trim(), i);
            }

            for (int i = 2; i < lines.length; i++) { // Skip header lines
                String[] values = lines[i].split(",", -1); // -1 keeps trailing empty strings
                if (values.length < headers.length) continue;

                try {
                    String ip = values[columnMap.getOrDefault("IP", -1)];
                    if (ip == null || ip.isEmpty() || ip.equals("null")) continue;

                    String countryLong = values[columnMap.getOrDefault("CountryLong", -1)];
                    String hostName = values[columnMap.getOrDefault("HostName", -1)];
                    String operatorClass = values[columnMap.getOrDefault("OperatorClass", -1)];
                    String speedStr = values[columnMap.getOrDefault("Speed", -1)];
                    String scoreStr = values[columnMap.getOrDefault("Score", -1)];
                    String uptimeStr = values[columnMap.getOrDefault("Uptime", -1)];
                    String totalUserStr = values[columnMap.getOrDefault("TotalUser", -1)];
                    String totalTrafficStr = values[columnMap.getOrDefault("TotalTraffic", -1)];
                    String logTypeStr = values[columnMap.getOrDefault("LogType", -1)];
                    String operatorStr = values[columnMap.getOrDefault("Operator", -1)];
                    String messageStr = values[columnMap.getOrDefault("Message", -1)];
                    String opentypeStr = values[columnMap.getOrDefault("OpenVPN_ProxyTransport_UDP", -1)];

                    // Skip if not suitable for OpenVPN UDP (most common)
                    if (!"1".equals(opentypeStr)) continue;

                    double speed = 0;
                    try { speed = Double.parseDouble(speedStr); } catch (NumberFormatException e) {}

                    int score = 0;
                    try { score = Integer.parseInt(scoreStr); } catch (NumberFormatException e) {}

                    long uptime = 0;
                    try { uptime = Long.parseLong(uptimeStr); } catch (NumberFormatException e) {}

                    VpnRotatorService.VpnServer server = new VpnRotatorService.VpnServer(
                            hostName != null ? hostName : ip,
                            hostName != null ? hostName : ip,
                            "udp"
                    );
                    server.ip = ip;
                    server.country = countryLong != null ? countryLong : "Unknown";
                    server.speed = speed;
                    server.score = score;
                    server.uptime = uptime;
                    servers.add(server);

                } catch (Exception e) {
                    Log.w(TAG, "Error parsing server line: " + Arrays.toString(values), e);
                }
            }

            // Sort by score (descending) and take top servers
            servers.sort((a, b) -> Integer.compare(b.score, a.score));
            return servers.size() > 50 ? servers.subList(0, 50) : servers;

        } catch (Exception e) {
            Log.e(TAG, "Error fetching VPN Gate servers", e);
            return null;
        }
    }
}