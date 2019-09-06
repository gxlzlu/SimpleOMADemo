package com.example.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import java.io.IOException;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, CallBackLog {

    private Button mBtnConnect;
    private Button mBtnSend;
    private EditText mTxtAid;
    private EditText mTxtApdu;
    private EditText mTxtResult;

    private OMAPI mOM;

    private final static byte[] TestAID = {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x87, (byte)0x10, (byte)0x02, (byte)0xFF, (byte)0x86, (byte)0xFF, (byte)0x03, (byte)0x89, (byte)0x93, (byte)0x8F, (byte)0x4F, (byte)0xE1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtnConnect = (Button) findViewById(R.id.btn_Connect);
        mBtnSend = (Button) findViewById(R.id.btn_Send);
        mTxtAid = (EditText) findViewById(R.id.txt_AID);
        mTxtApdu = (EditText) findViewById(R.id.txt_APDU);
        mTxtResult = (EditText) findViewById(R.id.txt_Result);
        mBtnConnect.setOnClickListener(this);
        mBtnSend.setOnClickListener(this);

        mOM = OMAPI.getInstance(this.getApplicationContext(), this);

        mTxtAid.setText(OMAPI.Bytes2Str(TestAID));

    }


    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.btn_Connect:
                btnConnectClick(v);
                break;
            case R.id.btn_Send:
                btnSendClick(v);
                break;
                default:
                    return;
        }

    }

    private void btnConnectClick(View v)
    {
        if (mTxtAid.getText().length() < 12)
        {
            log("AID Error: too short\r\n");
        }
        mOM.openLogicalChannel(OMAPI.Str2Bytes(mTxtAid.getText().toString()));

    }

    private void btnSendClick(View v)
    {
        byte[] tmp = OMAPI.Str2Bytes(mTxtApdu.getText().toString());

        try {
            tmp = mOM.SendAPDU(tmp);
        } catch (Exception e) {
            log(e.toString());
            return;
        }
        String response = OMAPI.Bytes2Str(tmp);
        mTxtResult.setText(response);
    }


    @Override
    public void log(String str) {
        String tmp = mTxtResult.getText().toString();
        mTxtResult.setText(tmp + "\r\n" + str);
    }
}
