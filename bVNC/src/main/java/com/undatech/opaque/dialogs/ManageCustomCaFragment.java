/**
 * Copyright (C) 2013- Iordan Iordanov
 * <p>
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */


package com.undatech.opaque.dialogs;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.iiordanov.pubkeygenerator.GeneratePubkeyActivity;
import com.undatech.opaque.ConnectionSettings;
import com.undatech.opaque.util.HttpsFileDownloader;
import com.undatech.remoteClientUi.R;

import java.io.File;
import java.io.InputStream;

public class ManageCustomCaFragment extends DialogFragment
        implements HttpsFileDownloader.OnDownloadFinishedListener {
    public static String TAG = "ManageCustomCaFragment";
    public static int TYPE_OVIRT = 0;
    public static int TYPE_SPICE = 1;
    private static final int IMPORT_CA_REQUEST = 0;

    @Override
    public void onDownload(String contents) {
        Log.d(TAG, "onDownload");
        caTextContents = contents;
        handler.post(setCaText);
        handler.post(() -> Toast.makeText(
                ManageCustomCaFragment.this.getActivity(),
                android.R.string.yes,
                Toast.LENGTH_SHORT
        ).show());
    }

    @Override
    public void onDownloadFailure() {
        Log.d(TAG, "onDownloadFailure");
        handler.post(() -> Toast.makeText(
                ManageCustomCaFragment.this.getActivity(),
                R.string.error_connection_failed,
                Toast.LENGTH_SHORT
        ).show());
    }

    public interface OnFragmentDismissedListener {
        void onFragmentDismissed(ConnectionSettings currentConnection);
    }

    private OnFragmentDismissedListener dismissalListener;
    private int caPurpose;
    private ConnectionSettings currentConnection;
    private Handler handler;
    private String caTextContents = "";
    private Runnable setCaText = new Runnable() {
        @Override
        public void run() {
            caCert.setText(caTextContents);
        }
    };

    private EditText caCert;
    private Button importButton;
    private Button downloadButton;
    private Button helpButton;

    public ManageCustomCaFragment() {
    }

    public void setOnFragmentDismissedListener(OnFragmentDismissedListener dismissalListener) {
        this.dismissalListener = dismissalListener;
    }

    public static ManageCustomCaFragment newInstance(int caPurpose, ConnectionSettings currentConnection) {
        ManageCustomCaFragment f = new ManageCustomCaFragment();

        // Supply the CA purpose as an argument.
        Bundle args = new Bundle();
        args.putInt("caPurpose", caPurpose);
        args.putSerializable("currentConnection", currentConnection);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            dismissalListener = (OnFragmentDismissedListener) activity;
            android.util.Log.e(TAG, "onAttach: assigning OnFragmentDismissedListener");
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnFragmentDismissedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        caPurpose = getArguments().getInt("caPurpose");
        currentConnection = (ConnectionSettings) getArguments().getSerializable("currentConnection");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.manage_custom_ca, container, false);

        // Set title for this dialog
        getDialog().setTitle(R.string.manage_custom_ca_title);

        caCert = (EditText) v.findViewById(R.id.caCert);

        // Set up the import button.
        importButton = (Button) v.findViewById(R.id.importButton);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCaCertFromFile();
            }
        });

        // Set up the import from server button.
        downloadButton = (Button) v.findViewById(R.id.downloadButton);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadFromServer();
            }
        });

        // Set up the help button.
        helpButton = (Button) v.findViewById(R.id.helpButton);
        helpButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //TODO: Show help
            }
        });

        // Set the widgets' state appropriately.
        setWidgetStateAppropriately();
        return v;
    }

    private void downloadFromServer() {
        Log.d(TAG, "downloadFromServer");
        String address = currentConnection.getAddress();
        if (!currentConnection.getAddress().startsWith("http")) {
            address = "https://" + address;
        }
        address += "/ovirt-engine/services/pki-resource?resource=ca-certificate&format=X509-PEM-CA";
        new HttpsFileDownloader(address, false,
                ManageCustomCaFragment.this).initiateDownload();
    }

    private void importCaCertFromFile() {
        Log.d(TAG, "importCaCertFromFile");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "*/*"
        });
        startActivityForResult(intent, IMPORT_CA_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        android.util.Log.i(TAG, "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case IMPORT_CA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    Activity activity = getActivity();
                    if (data != null && data.getData() != null && activity != null) {
                        ContentResolver resolver = activity.getContentResolver();
                        InputStream in = GeneratePubkeyActivity.getInputStreamFromUri(resolver, data.getData());
                        String keyData = GeneratePubkeyActivity.readFile(in);
                        caCert.setText(keyData);
                    } else {
                        Toast.makeText(activity, R.string.ca_file_error_reading, Toast.LENGTH_LONG).show();
                    }
                }
        }
    }

    private void setWidgetStateAppropriately() {
        if (caPurpose == ManageCustomCaFragment.TYPE_OVIRT) {
            caCert.setText(currentConnection.getOvirtCaData());
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        android.util.Log.e(TAG, "dismiss: sending back data to Activity");
        // Depending on the value of caPurpose, assign the certs in currentConnection.
        if (caPurpose == ManageCustomCaFragment.TYPE_OVIRT) {
            android.util.Log.e(TAG, "Setting custom oVirt CA");
            currentConnection.setOvirtCaData(caCert.getText().toString());
        }

        dismissalListener.onFragmentDismissed(currentConnection);
    }

    public String getExternalSDCardDirectory() {
        File dir = Environment.getExternalStorageDirectory();
        return dir.getAbsolutePath() + "/";
    }

}
