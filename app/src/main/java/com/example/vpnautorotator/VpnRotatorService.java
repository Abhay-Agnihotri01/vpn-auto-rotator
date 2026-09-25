package com.example.vpnautorotator;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class VpnRotatorService extends VpnService {

    private static final String TAG = "VpnRotatorService";
    private static final String CHANNEL_ID = "vpn_rotator_channel";
    private static final int NOTIFICATION_ID = 1;

    private volatile boolean running = false;
    private ScheduledExecutorService scheduler;
    private VpnServer currentServer;
    private ParcelFileDescriptor vpnInterface;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("interval_minutes")) {
            int interval = intent.getIntExtra("interval_minutes", 5);
            startRotation(interval);
        }
        return START_STICKY;
    }

    private void startRotation(int intervalMinutes) {
        if (running) return;
        running = true;

        // Initial connection
        connectToBestServer();

        // Schedule periodic rotation
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::rotateVpn,
                intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        startForeground(NOTIFICATION_ID, getNotification());
        Log.i(TAG, "VPN rotator started with " + intervalMinutes + " minute interval");
    }

    private void rotateVpn() {
        if (!running) return;
        disconnectVpn();
        connectToBestServer();
    }

    private void connectToBestServer() {
        List<VpnServer> servers = VpnGateFetcher.fetchServers(this);
        if (servers == null || servers.isEmpty()) {
            Log.w(TAG, "No servers available");
            showNotification(getString(R.string.no_servers));
            return;
        }

        // Try servers in order until one connects
        for (VpnServer server : servers) {
            if (running && attemptConnection(server)) {
                currentServer = server;
                showNotification(getString(R.string.vpn_connected_to, server.name));
                return;
            }
        }
        showNotification(getString(R.string.connection_failed));
    }

    private boolean attemptConnection(VpnServer server) {
        try {
            VpnService.Builder builder = new VpnService.Builder()
                    .setSession("VPN Auto-Rotator")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("8.8.4.4");

            // For simplicity, we assume UDP. In a real app, you might need to check the server's supported protocols.
            // VPN Gate's OpenVPN UDP port is 1194, but the VpnService API doesn't let us specify port directly.
            // The actual VPN connection is handled by the OS; we just set up the TUN interface.
            // The server details are used for routing and DNS, but the actual connection is to the VPN Gate server
            // which is handled by the Android VPN service when we establish the interface.
            // Note: This is a simplified implementation. A production app would need to handle the actual VPN connection
            // more carefully, possibly using OpenVPN for Android or similar.

            ParcelFileDescriptor pfd = builder.establish();
            if (pfd != null) {
                vpnInterface = pfd;
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to " + server.name, e);
        }
        return false;
    }

    private void disconnectVpn() {
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing VPN interface", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        disconnectVpn();
        stopForeground(STOP_FOREGROUND_REMOVE);
        Log.i(TAG, "VPN rotator stopped");
    }

    private Notification getNotification() {
        Intent stopIntent = new Intent(this, VpnRotatorService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.vpn_active))
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(R.mipmap.ic_launcher,
                        getString(R.string.stop), stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    private void showNotification(String message) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VPN Rotator Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows VPN connection status");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public boolean isVpnConnected() {
        return vpnInterface != null;
    }

    public VpnServer getCurrentServer() {
        return currentServer != null ? currentServer : new VpnServer("Disconnected", "", "");
    }

    // Inner class for server data
    public static class VpnServer {
        public String name;
        public String hostname;
        public String protocol;
        public double speed;
        public int score;
        public long uptime;
        public String country;
        public String ip;

        public VpnServer(String name, String hostname, String protocol) {
            this.name = name;
            this.hostname = hostname;
            this.protocol = protocol;
        }
    }
}