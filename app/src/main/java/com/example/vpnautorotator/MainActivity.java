package com.example.vpnautorotator;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "VPNRotatorPrefs";
    private static final String KEY_INTERVAL = "rotation_interval_minutes";
    private static final String KEY_ENABLED = "vpn_enabled";

    private SwitchCompat vpnSwitch;
    private SeekBar intervalSeekBar;
    private TextView intervalTextView, statusTextView;
    private Button refreshServersButton;
    private ListView serversListView;
    private ServerAdapter serverAdapter;
    private List<VpnServer> vpnServers = new ArrayList<>();

    private VpnRotatorService vpnService;
    private boolean isServiceBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VpnRotatorService.LocalBinder binder = (VpnRotatorService.LocalBinder) service;
            vpnService = binder.getService();
            isServiceBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        vpnSwitch = findViewById(R.id.vpnSwitch);
        intervalSeekBar = findViewById(R.id.intervalSeekBar);
        intervalTextView = findViewById(R.id.intervalTextView);
        statusTextView = findViewById(R.id.statusTextView);
        refreshServersButton = findViewById(R.id.refreshServersButton);
        serversListView = findViewById(R.id.serversListView);

        // Setup UI
        intervalSeekBar.setMax(55); // 5-60 minutes
        intervalSeekBar.setProgress(4); // Default 5 minutes
        updateIntervalText();

        intervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateIntervalText();
                saveSettings();
            }
            @Override public void onStartTrackingSeekBar(SeekBar seekBar) {}
            @Override public void onStopTrackingSeekBar(SeekBar seekBar) { saveSettings(); }
        });

        vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startVpnService();
            } else {
                stopVpnService();
            }
            saveSettings();
        });

        refreshServersButton.setOnClickListener(v -> fetchVpnServers());

        // Load saved settings
        loadSettings();
        fetchVpnServers();
    }

    private void updateIntervalText() {
        int minutes = intervalSeekBar.getProgress() + 5;
        intervalTextView.setText(getString(R.string.interval_format, minutes));
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_INTERVAL, intervalSeekBar.getProgress());
        editor.putBoolean(KEY_ENABLED, vpnSwitch.isChecked());
        editor.apply();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int interval = prefs.getInt(KEY_INTERVAL, 4); // Default 5 min
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        intervalSeekBar.setProgress(interval);
        vpnSwitch.setChecked(enabled);
        updateIntervalText();
    }

    private void startVpnService() {
        Intent intent = new Intent(this, VpnRotatorService.class);
        intent.putExtra("interval_minutes", intervalSeekBar.getProgress() + 5);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        updateUI();
    }

    private void stopVpnService() {
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        stopService(new Intent(this, VpnRotatorService.class));
        updateUI();
    }

    private void fetchVpnServers() {
        refreshServersButton.setEnabled(false);
        refreshServersButton.setText(R.string.refreshing);
        new Thread(() -> {
            List<VpnServer> servers = VpnGateFetcher.fetchServers(this);
            runOnUiThread(() -> {
                vpnServers.clear();
                if (servers != null && !servers.isEmpty()) {
                    vpnServers.addAll(servers);
                    serverAdapter = new ServerAdapter(this, vpnServers);
                    serversListView.setAdapter(serverAdapter);
                    statusTextView.setText(getString(R.string.servers_found, servers.size()));
                } else {
                    statusTextView.setText(R.string.fetch_failed);
                }
                refreshServersButton.setEnabled(true);
                refreshServersButton.setText(R.string.refresh_servers);
            });
        }).start();
    }

    private void updateUI() {
        boolean isConnected = isServiceBound && vpnService != null && vpnService.isVpnConnected();
        vpnSwitch.setChecked(isConnected);
        statusTextView.setText(isConnected ?
                getString(R.string.vpn_connected, vpnService.getCurrentServer().name) :
                getString(R.string.vpn_disconnected));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
        }
    }
}