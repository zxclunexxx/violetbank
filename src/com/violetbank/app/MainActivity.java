package com.violetbank.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF6D28D9);
        getWindow().setNavigationBarColor(0xFF120B24);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(new BankView(this));
    }

    @Override
    public void onBackPressed() {
        BankView view = (BankView) findViewById(BankView.VIEW_ID);
        if (view != null && view.goBack()) return;
        super.onBackPressed();
    }
}
