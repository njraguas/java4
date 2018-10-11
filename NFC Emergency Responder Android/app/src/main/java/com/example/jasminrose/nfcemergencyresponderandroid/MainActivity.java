package com.example.jasminrose.nfcemergencyresponderandroid;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapView;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    //to Servers - MODIFIED
    boolean accepting;
    String host = "localhost"; // change if necessary
    String timeStamp;

    //NFC
    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;
    IntentFilter[] intentFiltersArray;

    //Layout, views
    TextView txtUserId, txtFullName;
    ListView listContact;
    ConstraintLayout nfcApproach;
    LinearLayout nfcDetails;

    MapView mapView;
    MapViewLocation mapViewLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Label TextViews
        nfcApproach = findViewById(R.id.layout_nfc_approach);
        nfcDetails = findViewById(R.id.layout_nfc_details);

        //Value TextViews
        txtUserId = findViewById(R.id.txt_userid);
        txtFullName = findViewById(R.id.txt_fullname);

        //ListView for emergency contact numbers associated with users
        listContact = findViewById(R.id.list_contact);

        mapView = findViewById(R.id.mapView);
        mapViewLocation = new MapViewLocation(this, mapView);
        mapViewLocation.onCreate();
        mapView.onCreate(savedInstanceState);


        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            Toast.makeText(this, "Device doesn't support NFC", Toast.LENGTH_LONG);
            finish();
            return;
        }

        if (!nfcAdapter.isEnabled())
            Toast.makeText(this, "NFC is disabled", Toast.LENGTH_LONG);


        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(getIntent().getAction()) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            nfcApproach.setVisibility(View.INVISIBLE);
            nfcDetails.setVisibility(View.VISIBLE);
        }

        if(accepting) { // to see if server open - modified
            sendData();
        } else {
            saveToDb();
        }

        //readFromIntent(getIntent());
    }

    @Override
    public void onStart() {
        super.onStart();
        mapViewLocation.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        intentFiltersArray = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED), new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};

        if (nfcAdapter != null)
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();

        if (nfcAdapter != null)
            nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapViewLocation.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readFromIntent(intent);

        nfcApproach.setVisibility(View.INVISIBLE);
        nfcDetails.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mapViewLocation.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void readFromIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            byte[] nfcId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
            String hexdump = new String();

            for (int i = 0; i < nfcId.length; i++) {
                String x = Integer.toHexString(((int) nfcId[i] & 0xff));

                if (x.length() == 1)
                    x = '0' + x;

                hexdump += x + ' ';
            }

            txtUserId.setText(hexdump.toUpperCase().trim());

            Parcelable[] rawMessges = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] messages = null;

            if (rawMessges != null) {
                messages = new NdefMessage[rawMessges.length];

                for (int i = 0; i < rawMessges.length; i++)
                    messages[i] = (NdefMessage) rawMessges[i];
            }

            buildTagViews(messages);
        }
    }

    private void buildTagViews(NdefMessage[] messages) {
        txtFullName.setText(returnTextRecord(messages, 0));
        List<Map<String, String>> records = new ArrayList<>();
        String r;

        for (int i = 1; i < messages[0].getRecords().length; i++) {
            byte[] type = messages[0].getRecords()[i].getType();
            Log.i("typendef", type.toString());

            if (Arrays.equals(type, NdefRecord.RTD_TEXT)) {
                r = returnTextRecord(messages, i);
                Log.i("contactName", r);

                //To be put in the listview
                Map<String, String> record = new HashMap<>();
                record.put("name", r.substring(0, r.indexOf('-')));
                record.put("number", r.substring(r.indexOf('-') + 1));

                String location = mapViewLocation.getLocationName();

                //SMS Message to be sent to emergency numbers
                String txt = "Hi " + record.get("name") + ". This text message is to inform that "
                        + txtFullName.getText() + " is in an emergency!";

                String txt2 = "He/she is currently located in " + location + ".";


                TextMessage txtmessage = new TextMessage(this, record.get("number"), txt);
                txtmessage.sendSms();
                txtmessage.setMessage(txt2);
                txtmessage.sendSms();

                records.add(record);
            }
        }

        //Adapter to Emergency Contact Listview
        SimpleAdapter adapter = new SimpleAdapter(this, records,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "number"},
                new int[]{android.R.id.text1, android.R.id.text2});
        listContact.setAdapter(adapter);
    }

    public String returnTextRecord(NdefMessage[] messages, int recordIndex) {
        if (messages == null || messages.length == 0)
            return null;

        String text;

        byte[] payload = messages[0].getRecords()[recordIndex].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int langCodeLength = payload[0] & 0063;
        try {
            text = new String(payload, langCodeLength + 1, payload.length - langCodeLength - 1,
                    textEncoding);

            return text;
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }

        return null;
    }

    //MODIFIED PART
    // server send
    public void sendData() {
        MapViewLocation mapViewLocation = new MapViewLocation();
        timeStamp = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new Date());
        String message = txtUserId.getText().toString() + "," + timeStamp + "," + mapViewLocation.getLocationName();

        BackGroundTask backGroundTask = new BackGroundTask();
        backGroundTask.execute(message);
    }

    public void saveToDb() {
        MapViewLocation mapViewLocation = new MapViewLocation();
        timeStamp = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new Date());

        DatabaseHelper dbh = new DatabaseHelper(MainActivity.this);
        SQLiteDatabase db = dbh.getWritableDatabase();

        ContentValues localRecordValues = new ContentValues();
        localRecordValues.put(LocalRecords.USER_ID, txtUserId.getText().toString());
        localRecordValues.put(LocalRecords.DATE_TIME, timeStamp);
        localRecordValues.put(LocalRecords.LOCATION, mapViewLocation.getLocationName());

        db.insert(LocalRecords.TABLE_NAME, null, localRecordValues);
    }

    class BackGroundTask extends AsyncTask<String, Void, Void> {
        Socket s;
        PrintWriter writer;

        @Override
        protected Void doInBackground(String... voids) {
            try {
                String message = voids[0];
                s = new Socket(host, 5000);
                writer = new PrintWriter(s.getOutputStream());
                writer.write(message);
                writer.flush();
                writer.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

    }
}
